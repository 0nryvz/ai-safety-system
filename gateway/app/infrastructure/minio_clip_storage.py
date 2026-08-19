from datetime import datetime, timezone
import hashlib
from pathlib import Path
from urllib3 import PoolManager, Retry
from urllib3.util import Timeout

from app.services.clip_storage import (
    ClipStorage,
    ClipStorageError,
    ClipStorageResult,
)


def build_clip_object_key(
        *,
        violation_id: str,
        recording_id: str,
        clip_started_at: datetime,
) -> str:
    started_at_utc = _to_utc(clip_started_at)

    return (
        f"violations/{started_at_utc:%Y}/{started_at_utc:%m}/"
        f"{violation_id}/{recording_id}.mp4"
    )


class MinioClipStorage(ClipStorage):
    def __init__(
            self,
            *,
            endpoint: str,
            access_key: str,
            secret_key: str,
            bucket: str,
            secure: bool,
            minio_client: object | None = None,
    ) -> None:
        self._bucket = bucket
        self._endpoint = endpoint
        self._access_key = access_key
        self._secret_key = secret_key
        self._secure = secure

        if minio_client is not None:
            self._minio_client = minio_client
            return

        self._minio_client = None

    def store_finalized_clip(
            self,
            *,
            violation_id: str,
            recording_id: str,
            finalized_mp4_path: Path,
            clip_started_at: datetime,
    ) -> ClipStorageResult:
        minio_client = self._get_minio_client()

        clip_path = Path(finalized_mp4_path)
        local_size_bytes = clip_path.stat().st_size
        checksum = _compute_sha256_checksum(clip_path)
        object_key = build_clip_object_key(
            violation_id=violation_id,
            recording_id=recording_id,
            clip_started_at=clip_started_at,
        )

        try:
            with clip_path.open("rb") as clip_file:
                minio_client.put_object(
                    bucket_name=self._bucket,
                    object_name=object_key,
                    data=clip_file,
                    length=local_size_bytes,
                    content_type="video/mp4",
                    metadata={
                        "violationid": violation_id,
                        "recordingid": recording_id,
                        "checksum": checksum,
                    },
                )

            stat_result = minio_client.stat_object(
                bucket_name=self._bucket,
                object_name=object_key,
            )
        except Exception as ex:
            raise ClipStorageError(
                "Could not upload clip to MinIO",
                retryable=True,
            ) from ex

        remote_size_bytes = getattr(stat_result, "size", None)

        if remote_size_bytes != local_size_bytes:
            raise ClipStorageError(
                f"Uploaded object size mismatch for key={object_key}: "
                f"local={local_size_bytes}, remote={remote_size_bytes}",
                retryable=False,
            )

        return ClipStorageResult(
            bucket=self._bucket,
            object_key=object_key,
            checksum=checksum,
            size_bytes=local_size_bytes,
        )

    def _get_minio_client(
            self,
    ) -> object:
        if self._minio_client is not None:
            return self._minio_client

        try:
            from minio import Minio
        except Exception as ex:  # pragma: no cover
            raise ClipStorageError(
                "MinIO SDK is not available",
                retryable=False,
            ) from ex

        http_client = PoolManager(
            timeout=Timeout(
                connect=2.0,
                read=10.0,
            ),
            retries=Retry(
                total=0,
                connect=0,
                read=0,
                redirect=0,
                status=0,
            ),
        )

        self._minio_client = Minio(
            endpoint=self._endpoint,
            access_key=self._access_key,
            secret_key=self._secret_key,
            secure=self._secure,
            http_client=http_client,
        )

        return self._minio_client


def _to_utc(value: datetime) -> datetime:
    if value.tzinfo is None:
        return value.replace(tzinfo=timezone.utc)

    return value.astimezone(timezone.utc)


def _compute_sha256_checksum(
        clip_path: Path,
) -> str:
    digest = hashlib.sha256()

    with clip_path.open("rb") as clip_file:
        while True:
            chunk = clip_file.read(65_536)
            if not chunk:
                break

            digest.update(chunk)

    return f"sha256:{digest.hexdigest()}"
