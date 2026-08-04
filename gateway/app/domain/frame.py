from dataclasses import dataclass
from datetime import datetime


@dataclass(frozen=True, slots=True)
class FramePacket:
    camera_id: str
    session_id: str
    captured_at: datetime
    content_type: str
    data: bytes

    @property
    def size_bytes(self) -> int:
        return len(self.data)