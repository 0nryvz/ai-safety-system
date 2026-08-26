from dataclasses import dataclass, field
from datetime import datetime
from uuid import uuid4


@dataclass(frozen=True, slots=True)
class FramePacket:
    camera_id: str
    session_id: str
    captured_at: datetime
    content_type: str
    data: bytes
    event_id: str = field(
        default_factory=lambda: str(uuid4())
    )

    @property
    def size_bytes(self) -> int:
        return len(self.data)