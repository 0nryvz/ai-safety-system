from dataclasses import dataclass
from datetime import datetime, timezone
import hashlib
from pathlib import Path

import pytest

from app.infrastructure.minio_clip_storage import (
    MinioClipStorage,
    build_clip_object_key,
    build_cover_object_key,
)
from app.services.clip_storage import ClipStorageError


@dataclass
class _FakeStatResult:
    size: int


class _FakeMinioClient:
    def __init__(
            self,
            *,
            stat_size: int | None = None,
            raise_on_put: Exception | None = None,
    ) -> None:
        self._stat_size = stat_size
        self._raise_on_put = raise_on_put
        self.put_calls: list[dict[str, object]] = []
        self.stat_calls: list[dict[str, object]] = []

    def put_object(self, **kwargs: object) -> None:
        if self._raise_on_put is not None:
            raise self._raise_on_put

        self.put_calls.append(kwargs)

    def stat_object(self, **kwargs: object) -> _FakeStatResult:
        self.stat_calls.append(kwargs)

        if self._stat_size is None:
            put_length = self.put_calls[-1]["length"]
            return _FakeStatResult(size=int(put_length))

        return _FakeStatResult(size=self._stat_size)


def test_build_clip_object_key_is_deterministic_and_utc() -> None:
    clip_started_at = datetime(
        2026,
        1,
        1,
        1,
        15,
        tzinfo=timezone.utc,
    )

    object_key_first = build_clip_object_key(
        violation_id="violation-123",
        recording_id="recording-456",
        clip_started_at=clip_started_at,
    )
    object_key_second = build_clip_object_key(
        violation_id="violation-123",
        recording_id="recording-456",
        clip_started_at=clip_started_at,
    )

    assert object_key_first == object_key_second
    assert (
        object_key_first
        == "violations/2026/01/violation-123/recording-456.mp4"
    )


def test_store_finalized_clip_uploads_mp4_and_verifies_stat(
        tmp_path: Path,
) -> None:
    clip_bytes = b"mp4-binary-content"
    clip_path = tmp_path / "clip.mp4"
    clip_path.write_bytes(clip_bytes)

    fake_client = _FakeMinioClient()
    storage = MinioClipStorage(
        endpoint="minio.internal:9000",
        access_key="test-access",
        secret_key="test-secret",
        bucket="private-recordings",
        secure=False,
        minio_client=fake_client,
    )

    result = storage.store_finalized_clip(
        violation_id="violation-1",
        recording_id="recording-1",
        finalized_mp4_path=clip_path,
        clip_started_at=datetime(
            2026,
            8,
            15,
            20,
            30,
            tzinfo=timezone.utc,
        ),
    )

    expected_checksum = (
        "sha256:"
        + hashlib.sha256(clip_bytes).hexdigest()
    )

    assert result.bucket == "private-recordings"
    assert result.object_key == (
        "violations/2026/08/violation-1/recording-1.mp4"
    )
    assert result.checksum == expected_checksum
    assert result.size_bytes == len(clip_bytes)

    assert len(fake_client.put_calls) == 1
    put_call = fake_client.put_calls[0]
    assert put_call["bucket_name"] == "private-recordings"
    assert put_call["object_name"] == (
        "violations/2026/08/violation-1/recording-1.mp4"
    )
    assert put_call["content_type"] == "video/mp4"
    assert put_call["length"] == len(clip_bytes)

    metadata = put_call["metadata"]
    assert isinstance(metadata, dict)
    assert metadata["violationid"] == "violation-1"
    assert metadata["recordingid"] == "recording-1"
    assert metadata["checksum"] == expected_checksum

    assert len(fake_client.stat_calls) == 1
    stat_call = fake_client.stat_calls[0]
    assert stat_call["bucket_name"] == "private-recordings"
    assert stat_call["object_name"] == (
        "violations/2026/08/violation-1/recording-1.mp4"
    )


def test_store_finalized_clip_raises_on_size_mismatch(
        tmp_path: Path,
) -> None:
    clip_path = tmp_path / "clip.mp4"
    clip_path.write_bytes(b"123456")

    fake_client = _FakeMinioClient(stat_size=7)
    storage = MinioClipStorage(
        endpoint="minio.internal:9000",
        access_key="test-access",
        secret_key="test-secret",
        bucket="private-recordings",
        secure=False,
        minio_client=fake_client,
    )

    with pytest.raises(ClipStorageError, match="size mismatch"):
        storage.store_finalized_clip(
            violation_id="violation-1",
            recording_id="recording-1",
            finalized_mp4_path=clip_path,
            clip_started_at=datetime.now(timezone.utc),
        )


def test_store_finalized_clip_wraps_minio_exception(
        tmp_path: Path,
) -> None:
    clip_path = tmp_path / "clip.mp4"
    clip_path.write_bytes(b"1234")

    fake_client = _FakeMinioClient(
        raise_on_put=RuntimeError("minio boom")
    )
    storage = MinioClipStorage(
        endpoint="minio.internal:9000",
        access_key="test-access",
        secret_key="test-secret",
        bucket="private-recordings",
        secure=False,
        minio_client=fake_client,
    )

    with pytest.raises(ClipStorageError) as exc_info:
        storage.store_finalized_clip(
            violation_id="violation-1",
            recording_id="recording-1",
            finalized_mp4_path=clip_path,
            clip_started_at=datetime.now(timezone.utc),
        )

    assert "Could not upload clip to MinIO" in str(exc_info.value)
    assert isinstance(exc_info.value.__cause__, RuntimeError)

def test_build_cover_object_key_is_deterministic() -> None:
    captured_at = datetime(
        2026,
        8,
        19,
        20,
        30,
        tzinfo=timezone.utc,
    )

    object_key = build_cover_object_key(
        violation_id="violation-1",
        captured_at=captured_at,
    )

    assert object_key == (
        "violations/2026/08/"
        "violation-1/cover.jpg"
    )


def test_store_cover_image_uploads_jpeg_and_verifies_stat(
) -> None:
    cover_bytes = (
        b"\xff\xd8"
        b"real-test-jpeg"
        b"\xff\xd9"
    )

    fake_client = _FakeMinioClient()

    storage = MinioClipStorage(
        endpoint="minio.internal:9000",
        access_key="test-access",
        secret_key="test-secret",
        bucket="private-recordings",
        secure=False,
        minio_client=fake_client,
    )

    object_key = storage.store_cover_image(
        violation_id="violation-1",
        recording_id="recording-1",
        image_bytes=cover_bytes,
        captured_at=datetime(
            2026,
            8,
            19,
            20,
            30,
            tzinfo=timezone.utc,
        ),
    )

    assert object_key == (
        "violations/2026/08/"
        "violation-1/cover.jpg"
    )

    assert len(
        fake_client.put_calls
    ) == 1

    put_call = (
        fake_client.put_calls[0]
    )

    assert (
            put_call["object_name"]
            == object_key
    )

    assert (
            put_call["content_type"]
            == "image/jpeg"
    )

    assert (
            put_call["length"]
            == len(cover_bytes)
    )

    assert len(
        fake_client.stat_calls
    ) == 1
