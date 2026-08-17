import os
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path

import pytest

from app.services.clip_delivery_coordinator import (
    ClipDeliveryCommand,
    ClipDeliveryCoordinator,
    ClipDeliveryError,
)
from app.services.clip_storage import (
    ClipStorageError,
    ClipStorageResult,
)
from app.services.clip_spool import LocalClipSpool
from app.services.recording_callback_client import (
    RecordingCallbackError,
    RecordingCallbackPayload,
)


class _FakeClipStorage:
    def __init__(
            self,
            *,
            result: ClipStorageResult | None = None,
            raise_error: Exception | None = None,
            errors: list[Exception] | None = None,
            events: list[str] | None = None,
    ) -> None:
        self._result = result
        self._raise_error = raise_error
        self._errors = list(errors or [])
        self._events = events
        self.calls: list[dict[str, object]] = []

    def store_finalized_clip(
            self,
            *,
            violation_id: str,
            recording_id: str,
            finalized_mp4_path: Path,
            clip_started_at: datetime,
    ) -> ClipStorageResult:
        if self._events is not None:
            self._events.append("upload")

        self.calls.append(
            {
                "violation_id": violation_id,
                "recording_id": recording_id,
                "finalized_mp4_path": finalized_mp4_path,
                "clip_started_at": clip_started_at,
            }
        )

        if self._errors:
            raise self._errors.pop(0)

        if self._raise_error is not None:
            raise self._raise_error

        assert self._result is not None
        return self._result


class _FakeRecordingCallbackClient:
    def __init__(
            self,
            *,
            raise_error: Exception | None = None,
            errors: list[Exception] | None = None,
            events: list[str] | None = None,
    ) -> None:
        self._raise_error = raise_error
        self._errors = list(errors or [])
        self._events = events
        self.calls: list[RecordingCallbackPayload] = []

    async def send_callback(
            self,
            payload: RecordingCallbackPayload,
    ) -> None:
        if self._events is not None:
            self._events.append("callback")

        self.calls.append(payload)

        if self._errors:
            raise self._errors.pop(0)

        if self._raise_error is not None:
            raise self._raise_error


def _build_command() -> ClipDeliveryCommand:
    return ClipDeliveryCommand(
        recording_id="recording-1",
        violation_id="violation-1",
        started_at=datetime(
            2026,
            8,
            16,
            12,
            0,
            tzinfo=timezone.utc,
        ),
        output_path=Path("C:/tmp/finalized.mp4"),
        duration_ms=30_000,
        size_bytes=12345,
    )


def _write_file(path: Path, size: int) -> None:
    path.parent.mkdir(
        parents=True,
        exist_ok=True,
    )
    path.write_bytes(b"a" * size)


def _make_expired(path: Path, *, seconds_ago: int) -> None:
    timestamp = time.time() - seconds_ago
    os.utime(path, (timestamp, timestamp))


@pytest.mark.asyncio
async def test_deliver_ready_success_upload_then_callback() -> None:
    events: list[str] = []

    storage_result = ClipStorageResult(
        bucket="private-recordings",
        object_key="violations/2026/08/violation-1/recording-1.mp4",
        checksum="sha256:abcd",
        size_bytes=12345,
    )
    storage = _FakeClipStorage(
        result=storage_result,
        events=events,
    )
    callback_client = _FakeRecordingCallbackClient(
        events=events,
    )
    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
    )

    command = _build_command()

    result = await coordinator.deliver_ready(command)

    assert result == storage_result
    assert events == ["upload", "callback"]

    assert len(storage.calls) == 1
    storage_call = storage.calls[0]
    assert storage_call["recording_id"] == "recording-1"
    assert storage_call["violation_id"] == "violation-1"
    assert storage_call["finalized_mp4_path"] == Path("C:/tmp/finalized.mp4")

    assert len(callback_client.calls) == 1
    callback_payload = callback_client.calls[0]
    assert callback_payload.recording_id == "recording-1"
    assert callback_payload.violation_id == "violation-1"
    assert callback_payload.status == "READY"
    assert callback_payload.object_key == storage_result.object_key
    assert callback_payload.checksum == storage_result.checksum
    assert callback_payload.size_bytes == storage_result.size_bytes
    assert callback_payload.duration_ms == 30_000


@pytest.mark.asyncio
async def test_deliver_ready_terminal_upload_error_sends_error_callback() -> None:
    storage = _FakeClipStorage(
        raise_error=ClipStorageError(
            "upload failed",
            retryable=False,
        ),
    )

    callback_client = _FakeRecordingCallbackClient()

    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
    )

    command = ClipDeliveryCommand(
        recording_id="recording-1",
        violation_id="violation-1",
        started_at=datetime.now(timezone.utc),
        output_path=Path("C:/tmp/finalized.mp4"),
        duration_ms=30_000,
        size_bytes=12345,
    )

    with pytest.raises(
            ClipDeliveryError,
            match="Clip upload failed",
    ):
        await coordinator.deliver_ready(command)

    assert len(storage.calls) == 1
    assert len(callback_client.calls) == 1

    callback_payload = callback_client.calls[0]

    assert callback_payload.recording_id == "recording-1"
    assert callback_payload.violation_id == "violation-1"
    assert callback_payload.status == "ERROR"
    assert callback_payload.error_code == "CLIP_UPLOAD_FAILED"
    assert callback_payload.retry_count == 0
    assert callback_payload.object_key is None


@pytest.mark.asyncio
async def test_deliver_ready_callback_error_raises_controlled_error() -> None:
    storage_result = ClipStorageResult(
        bucket="private-recordings",
        object_key="violations/2026/08/violation-2/recording-2.mp4",
        checksum="sha256:efgh",
        size_bytes=54321,
    )
    storage = _FakeClipStorage(
        result=storage_result,
    )
    callback_client = _FakeRecordingCallbackClient(
        raise_error=RecordingCallbackError("backend down"),
    )
    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
    )

    with pytest.raises(ClipDeliveryError, match="callback failed") as exc_info:
        await coordinator.deliver_ready(
            ClipDeliveryCommand(
                recording_id="recording-2",
                violation_id="violation-2",
                started_at=datetime.now(timezone.utc),
                output_path=Path("C:/tmp/finalized.mp4"),
                duration_ms=15_000,
                size_bytes=54321,
            )
        )

    assert exc_info.value.storage_result == storage_result
    assert len(storage.calls) == 1
    assert len(callback_client.calls) == 1


@pytest.mark.asyncio
async def test_deliver_ready_retries_upload_after_transient_error() -> None:
    sleep_calls: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleep_calls.append(seconds)

    storage_result = ClipStorageResult(
        bucket="private-recordings",
        object_key="violations/2026/08/violation-1/recording-1.mp4",
        checksum="sha256:abcd",
        size_bytes=12345,
    )
    storage = _FakeClipStorage(
        result=storage_result,
        errors=[ClipStorageError("temporary upload error", retryable=True)],
    )
    callback_client = _FakeRecordingCallbackClient()
    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
        upload_max_retries=2,
        upload_initial_backoff_seconds=0.25,
        upload_max_backoff_seconds=2.0,
        sleep_func=fake_sleep,
    )

    result = await coordinator.deliver_ready(_build_command())

    assert result == storage_result
    assert len(storage.calls) == 2
    assert len(callback_client.calls) == 1
    assert sleep_calls == [0.25]


@pytest.mark.asyncio
async def test_deliver_ready_upload_retry_limit_raises_controlled_error() -> None:
    sleep_calls: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleep_calls.append(seconds)

    storage = _FakeClipStorage(
        errors=[
            ClipStorageError(
                "temporary upload error-1",
                retryable=True,
            ),
            ClipStorageError(
                "temporary upload error-2",
                retryable=True,
            ),
            ClipStorageError(
                "temporary upload error-3",
                retryable=True,
            ),
        ],
    )

    callback_client = _FakeRecordingCallbackClient()

    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
        upload_max_retries=2,
        upload_initial_backoff_seconds=0.2,
        upload_max_backoff_seconds=0.3,
        sleep_func=fake_sleep,
    )

    with pytest.raises(
            ClipDeliveryError,
            match="Clip upload failed",
    ):
        await coordinator.deliver_ready(
            _build_command()
        )

    assert len(storage.calls) == 3

    assert len(callback_client.calls) == 1

    callback_payload = callback_client.calls[0]

    assert callback_payload.recording_id == "recording-1"
    assert callback_payload.violation_id == "violation-1"
    assert callback_payload.status == "ERROR"
    assert callback_payload.error_code == "CLIP_UPLOAD_FAILED"
    assert callback_payload.object_key is None

    assert sleep_calls == [0.2, 0.3]


@pytest.mark.asyncio
async def test_deliver_ready_retries_callback_without_reupload_and_same_payload() -> None:
    sleep_calls: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleep_calls.append(seconds)

    storage_result = ClipStorageResult(
        bucket="private-recordings",
        object_key="violations/2026/08/violation-1/recording-1.mp4",
        checksum="sha256:abcd",
        size_bytes=12345,
    )
    storage = _FakeClipStorage(result=storage_result)
    callback_client = _FakeRecordingCallbackClient(
        errors=[RecordingCallbackError("server error", retryable=True)],
    )
    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
        callback_max_retries=2,
        callback_initial_backoff_seconds=0.5,
        callback_max_backoff_seconds=1.0,
        sleep_func=fake_sleep,
    )

    await coordinator.deliver_ready(_build_command())

    assert len(storage.calls) == 1
    assert len(callback_client.calls) == 2
    assert callback_client.calls[0] is callback_client.calls[1]
    assert sleep_calls == [0.5]


@pytest.mark.asyncio
async def test_deliver_ready_callback_permanent_4xx_not_retried() -> None:
    sleep_calls: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleep_calls.append(seconds)

    storage_result = ClipStorageResult(
        bucket="private-recordings",
        object_key="violations/2026/08/violation-1/recording-1.mp4",
        checksum="sha256:abcd",
        size_bytes=12345,
    )
    storage = _FakeClipStorage(result=storage_result)
    callback_client = _FakeRecordingCallbackClient(
        raise_error=RecordingCallbackError(
            "conflict",
            retryable=False,
        ),
    )
    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
        callback_max_retries=5,
        callback_initial_backoff_seconds=0.5,
        callback_max_backoff_seconds=2.0,
        sleep_func=fake_sleep,
    )

    with pytest.raises(ClipDeliveryError, match="callback failed"):
        await coordinator.deliver_ready(_build_command())

    assert len(storage.calls) == 1
    assert len(callback_client.calls) == 1
    assert sleep_calls == []


@pytest.mark.asyncio
async def test_deliver_ready_callback_retry_limit_raises_controlled_error() -> None:
    sleep_calls: list[float] = []

    async def fake_sleep(seconds: float) -> None:
        sleep_calls.append(seconds)

    storage_result = ClipStorageResult(
        bucket="private-recordings",
        object_key="violations/2026/08/violation-1/recording-1.mp4",
        checksum="sha256:abcd",
        size_bytes=12345,
    )
    storage = _FakeClipStorage(result=storage_result)
    callback_client = _FakeRecordingCallbackClient(
        errors=[
            RecordingCallbackError("temporary 5xx-1", retryable=True),
            RecordingCallbackError("temporary 5xx-2", retryable=True),
            RecordingCallbackError("temporary 5xx-3", retryable=True),
        ],
    )
    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
        callback_max_retries=2,
        callback_initial_backoff_seconds=0.4,
        callback_max_backoff_seconds=0.6,
        sleep_func=fake_sleep,
    )

    with pytest.raises(ClipDeliveryError, match="callback failed") as exc_info:
        await coordinator.deliver_ready(_build_command())

    assert exc_info.value.storage_result == storage_result
    assert len(storage.calls) == 1
    assert len(callback_client.calls) == 3
    assert sleep_calls == [0.4, 0.6]


@pytest.mark.asyncio
async def test_deliver_ready_success_deletes_local_mp4(
        tmp_path: Path,
) -> None:
    output_path = tmp_path / "ready.mp4"
    _write_file(output_path, 128)

    storage_result = ClipStorageResult(
        bucket="private-recordings",
        object_key="violations/2026/08/violation-1/recording-1.mp4",
        checksum="sha256:abcd",
        size_bytes=128,
    )
    storage = _FakeClipStorage(result=storage_result)
    callback_client = _FakeRecordingCallbackClient()
    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
        clip_spool=LocalClipSpool(
            output_dir=tmp_path,
            max_bytes=1024,
            ttl_seconds=3600,
        ),
    )

    command = ClipDeliveryCommand(
        recording_id="recording-1",
        violation_id="violation-1",
        started_at=datetime.now(timezone.utc),
        output_path=output_path,
        duration_ms=1000,
        size_bytes=128,
    )

    await coordinator.deliver_ready(command)

    assert not output_path.exists()


@pytest.mark.asyncio
async def test_deliver_ready_failed_delivery_retains_local_mp4(
        tmp_path: Path,
) -> None:
    output_path = tmp_path / "failed.mp4"
    _write_file(output_path, 128)

    storage_result = ClipStorageResult(
        bucket="private-recordings",
        object_key="violations/2026/08/violation-1/recording-1.mp4",
        checksum="sha256:abcd",
        size_bytes=128,
    )
    storage = _FakeClipStorage(result=storage_result)
    callback_client = _FakeRecordingCallbackClient(
        raise_error=RecordingCallbackError(
            "backend failed",
            retryable=False,
        ),
    )
    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
        clip_spool=LocalClipSpool(
            output_dir=tmp_path,
            max_bytes=1024,
            ttl_seconds=3600,
        ),
    )

    command = ClipDeliveryCommand(
        recording_id="recording-1",
        violation_id="violation-1",
        started_at=datetime.now(timezone.utc),
        output_path=output_path,
        duration_ms=1000,
        size_bytes=128,
    )

    with pytest.raises(ClipDeliveryError):
        await coordinator.deliver_ready(command)

    assert output_path.exists()


@pytest.mark.asyncio
async def test_deliver_ready_quota_exceeded_returns_controlled_error(
        tmp_path: Path,
) -> None:
    output_path = tmp_path / "current.mp4"
    _write_file(output_path, 256)
    _write_file(tmp_path / "old.mp4", 512)

    storage_result = ClipStorageResult(
        bucket="private-recordings",
        object_key="violations/2026/08/violation-1/recording-1.mp4",
        checksum="sha256:abcd",
        size_bytes=256,
    )
    storage = _FakeClipStorage(result=storage_result)
    callback_client = _FakeRecordingCallbackClient()
    coordinator = ClipDeliveryCoordinator(
        clip_storage=storage,
        recording_callback_client=callback_client,
        clip_spool=LocalClipSpool(
            output_dir=tmp_path,
            max_bytes=700,
            ttl_seconds=3600,
        ),
    )

    command = ClipDeliveryCommand(
        recording_id="recording-1",
        violation_id="violation-1",
        started_at=datetime.now(timezone.utc),
        output_path=output_path,
        duration_ms=1000,
        size_bytes=256,
    )

    with pytest.raises(ClipDeliveryError, match="spool"):
        await coordinator.deliver_ready(command)

    assert output_path.exists()
    assert len(storage.calls) == 0
    assert len(callback_client.calls) == 0


def test_local_clip_spool_cleans_only_expired_files_and_keeps_non_expired(
        tmp_path: Path,
) -> None:
    current_file = tmp_path / "current.mp4"
    expired_file = tmp_path / "expired.mp4"
    recent_file = tmp_path / "recent.mp4"

    _write_file(current_file, 100)
    _write_file(expired_file, 100)
    _write_file(recent_file, 100)

    _make_expired(expired_file, seconds_ago=120)
    _make_expired(current_file, seconds_ago=120)
    _make_expired(recent_file, seconds_ago=5)

    spool = LocalClipSpool(
        output_dir=tmp_path,
        max_bytes=1000,
        ttl_seconds=60,
    )

    spool.prepare_for_delivery(current_file)

    assert current_file.exists()
    assert not expired_file.exists()
    assert recent_file.exists()


def test_local_clip_spool_keeps_non_expired_file_within_ttl(
        tmp_path: Path,
) -> None:
    current_file = tmp_path / "current.mp4"
    recent_file = tmp_path / "recent.mp4"

    _write_file(current_file, 100)
    _write_file(recent_file, 100)
    _make_expired(current_file, seconds_ago=30)
    _make_expired(recent_file, seconds_ago=30)

    spool = LocalClipSpool(
        output_dir=tmp_path,
        max_bytes=1000,
        ttl_seconds=60,
    )

    spool.prepare_for_delivery(current_file)

    assert current_file.exists()
    assert recent_file.exists()
