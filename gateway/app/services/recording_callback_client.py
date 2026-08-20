from dataclasses import dataclass
from typing import Protocol


class RecordingCallbackError(RuntimeError):
    """Recording callback could not be delivered."""

    def __init__(
            self,
            message: str,
            *,
            retryable: bool = False,
    ) -> None:
        super().__init__(message)
        self.retryable = retryable


@dataclass(frozen=True, slots=True)
class RecordingCallbackPayload:
    recording_id: str
    violation_id: str
    status: str
    retry_count: int
    object_key: str | None = None
    cover_image_key: str | None = None
    duration_ms: int | None = None
    size_bytes: int | None = None
    checksum: str | None = None
    error_code: str | None = None

class RecordingCallbackClient(Protocol):
    async def send_callback(
            self,
            payload: RecordingCallbackPayload,
    ) -> None:
        ...
