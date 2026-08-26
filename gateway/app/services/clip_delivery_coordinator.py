import asyncio
from collections.abc import Awaitable, Callable
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from app.services.clip_spool import (
    ClipSpool,
    ClipSpoolError,
)
from app.services.clip_storage import (
    ClipStorage,
    ClipStorageError,
    ClipStorageResult,
)
from app.services.recording_callback_client import (
    RecordingCallbackClient,
    RecordingCallbackError,
    RecordingCallbackPayload,
)


class ClipDeliveryError(RuntimeError):
    """Finalized clip delivery failed."""

    def __init__(
            self,
            message: str,
            *,
            storage_result: ClipStorageResult | None = None,
    ) -> None:
        super().__init__(message)
        self.storage_result = storage_result


@dataclass(frozen=True, slots=True)
class ClipDeliveryCommand:
    recording_id: str
    violation_id: str
    started_at: datetime
    output_path: Path
    duration_ms: int
    size_bytes: int
    cover_image_bytes: bytes | None = None


class ClipDeliveryCoordinator:
    def __init__(
            self,
            *,
            clip_storage: ClipStorage,
            recording_callback_client: RecordingCallbackClient,
            upload_max_retries: int = 0,
            upload_initial_backoff_seconds: float = 0.0,
            upload_max_backoff_seconds: float = 0.0,
            callback_max_retries: int = 0,
            callback_initial_backoff_seconds: float = 0.0,
            callback_max_backoff_seconds: float = 0.0,
            clip_spool: ClipSpool | None = None,
            sleep_func: Callable[[float], Awaitable[None]] = asyncio.sleep,
    ) -> None:
        self._clip_storage = clip_storage
        self._recording_callback_client = recording_callback_client

        self._upload_max_retries = upload_max_retries
        self._upload_initial_backoff_seconds = (
            upload_initial_backoff_seconds
        )
        self._upload_max_backoff_seconds = (
            upload_max_backoff_seconds
        )

        self._callback_max_retries = callback_max_retries
        self._callback_initial_backoff_seconds = (
            callback_initial_backoff_seconds
        )
        self._callback_max_backoff_seconds = (
            callback_max_backoff_seconds
        )

        self._clip_spool = clip_spool
        self._sleep = sleep_func

    async def deliver_ready(
            self,
            command: ClipDeliveryCommand,
    ) -> ClipStorageResult:
        self._prepare_spool(command)

        try:
            try:
                storage_result = await self._store_with_retry(
                    command
                )
                cover_image_key = (
                    await self._store_cover_with_retry(
                        command
                    )
                )
            except ClipDeliveryError:
                error_callback_payload = RecordingCallbackPayload(
                    recording_id=command.recording_id,
                    violation_id=command.violation_id,
                    status="ERROR",
                    retry_count=0,
                    error_code="CLIP_UPLOAD_FAILED",
                )

                await self._send_error_callback_with_retry(
                    callback_payload=error_callback_payload,
                )

                raise

            ready_callback_payload = RecordingCallbackPayload(
                recording_id=command.recording_id,
                violation_id=command.violation_id,
                status="READY",
                object_key=storage_result.object_key,
                cover_image_key=cover_image_key,
                duration_ms=command.duration_ms,
                size_bytes=storage_result.size_bytes,
                checksum=storage_result.checksum,
                retry_count=0,
            )

            await self._send_ready_callback_with_retry(
                callback_payload=ready_callback_payload,
                storage_result=storage_result,
            )

            self._finalize_spool_success(command)

            return storage_result

        except ClipDeliveryError:
            self._mark_spool_failure(command)
            raise

    async def deliver_error(
            self,
            *,
            recording_id: str,
            violation_id: str,
            error_code: str,
    ) -> None:
        callback_payload = RecordingCallbackPayload(
            recording_id=recording_id,
            violation_id=violation_id,
            status="ERROR",
            retry_count=0,
            error_code=error_code,
        )

        await self._send_error_callback_with_retry(
            callback_payload=callback_payload,
        )

    def _prepare_spool(
            self,
            command: ClipDeliveryCommand,
    ) -> None:
        if self._clip_spool is None:
            return

        try:
            self._clip_spool.prepare_for_delivery(
                command.output_path
            )
        except ClipSpoolError as ex:
            raise ClipDeliveryError(
                "Clip spool preparation failed"
            ) from ex

    def _finalize_spool_success(
            self,
            command: ClipDeliveryCommand,
    ) -> None:
        if self._clip_spool is None:
            return

        try:
            self._clip_spool.finalize_success(
                command.output_path
            )
        except ClipSpoolError as ex:
            raise ClipDeliveryError(
                "Local clip cleanup failed"
            ) from ex

    def _mark_spool_failure(
            self,
            command: ClipDeliveryCommand,
    ) -> None:
        if self._clip_spool is None:
            return

        try:
            self._clip_spool.on_delivery_failure(
                command.output_path
            )
        except ClipSpoolError:
            return

    async def _store_with_retry(
            self,
            command: ClipDeliveryCommand,
    ) -> ClipStorageResult:
        attempt = 0

        while True:
            try:
                return self._clip_storage.store_finalized_clip(
                    violation_id=command.violation_id,
                    recording_id=command.recording_id,
                    finalized_mp4_path=command.output_path,
                    clip_started_at=command.started_at,
                )

            except ClipStorageError as ex:
                if (
                        not ex.retryable
                        or attempt >= self._upload_max_retries
                ):
                    raise ClipDeliveryError(
                        "Clip upload failed"
                    ) from ex

                await self._sleep_with_backoff(
                    retry_index=attempt,
                    initial_backoff_seconds=(
                        self._upload_initial_backoff_seconds
                    ),
                    max_backoff_seconds=(
                        self._upload_max_backoff_seconds
                    ),
                )

                attempt += 1


    async def _store_cover_with_retry(
            self,
            command: ClipDeliveryCommand,
    ) -> str | None:
        if command.cover_image_bytes is None:
            return None

        attempt = 0

        while True:
            try:
                return self._clip_storage.store_cover_image(
                    violation_id=command.violation_id,
                    recording_id=command.recording_id,
                    image_bytes=command.cover_image_bytes,
                    captured_at=command.started_at,
                )

            except ClipStorageError as ex:
                if (
                        not ex.retryable
                        or attempt
                        >= self._upload_max_retries
                ):
                    # Cover yardımcı medyadır.
                    # Cover başarısız diye sağlam MP4'ü
                    # ERROR durumuna düşürmüyoruz.
                    return None

                await self._sleep_with_backoff(
                    retry_index=attempt,
                    initial_backoff_seconds=(
                        self
                        ._upload_initial_backoff_seconds
                    ),
                    max_backoff_seconds=(
                        self
                        ._upload_max_backoff_seconds
                    ),
                )

                attempt += 1

    async def _send_ready_callback_with_retry(
            self,
            *,
            callback_payload: RecordingCallbackPayload,
            storage_result: ClipStorageResult,
    ) -> None:
        attempt = 0

        while True:
            try:
                await self._recording_callback_client.send_callback(
                    callback_payload
                )
                return

            except RecordingCallbackError as ex:
                if (
                        not ex.retryable
                        or attempt >= self._callback_max_retries
                ):
                    raise ClipDeliveryError(
                        "Recording READY callback failed",
                        storage_result=storage_result,
                    ) from ex

                await self._sleep_with_backoff(
                    retry_index=attempt,
                    initial_backoff_seconds=(
                        self._callback_initial_backoff_seconds
                    ),
                    max_backoff_seconds=(
                        self._callback_max_backoff_seconds
                    ),
                )

                attempt += 1

    async def _send_error_callback_with_retry(
            self,
            *,
            callback_payload: RecordingCallbackPayload,
    ) -> None:
        attempt = 0

        while True:
            try:
                await self._recording_callback_client.send_callback(
                    callback_payload
                )
                return

            except RecordingCallbackError as ex:
                if (
                        not ex.retryable
                        or attempt >= self._callback_max_retries
                ):
                    raise ClipDeliveryError(
                        "Recording ERROR callback failed"
                    ) from ex

                await self._sleep_with_backoff(
                    retry_index=attempt,
                    initial_backoff_seconds=(
                        self._callback_initial_backoff_seconds
                    ),
                    max_backoff_seconds=(
                        self._callback_max_backoff_seconds
                    ),
                )

                attempt += 1

    async def _sleep_with_backoff(
            self,
            *,
            retry_index: int,
            initial_backoff_seconds: float,
            max_backoff_seconds: float,
    ) -> None:
        delay_seconds = _compute_backoff_seconds(
            retry_index=retry_index,
            initial_backoff_seconds=initial_backoff_seconds,
            max_backoff_seconds=max_backoff_seconds,
        )

        if delay_seconds <= 0:
            return

        await self._sleep(delay_seconds)


def _compute_backoff_seconds(
        *,
        retry_index: int,
        initial_backoff_seconds: float,
        max_backoff_seconds: float,
) -> float:
    if initial_backoff_seconds <= 0:
        return 0.0

    delay_seconds = (
            initial_backoff_seconds * (2 ** retry_index)
    )

    if max_backoff_seconds <= 0:
        return delay_seconds

    return min(
        delay_seconds,
        max_backoff_seconds,
    )