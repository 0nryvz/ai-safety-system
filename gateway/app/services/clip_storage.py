from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from typing import Protocol


class ClipStorageError(RuntimeError):
    """Clip storage could not upload or verify the object."""

    def __init__(
            self,
            message: str,
            *,
            retryable: bool = True,
    ) -> None:
        super().__init__(message)
        self.retryable = retryable


@dataclass(frozen=True, slots=True)
class ClipStorageResult:
    bucket: str
    object_key: str
    checksum: str
    size_bytes: int


class ClipStorage(Protocol):

    def store_finalized_clip(
            self,
            *,
            violation_id: str,
            recording_id: str,
            finalized_mp4_path: Path,
            clip_started_at: datetime,
    ) -> ClipStorageResult:
        ...

    def store_cover_image(
            self,
            *,
            violation_id: str,
            recording_id: str,
            image_bytes: bytes,
            captured_at: datetime,
    ) -> str:
        ...