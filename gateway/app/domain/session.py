from dataclasses import dataclass, field
from datetime import datetime, timezone
from enum import StrEnum


def utc_now() -> datetime:
    return datetime.now(timezone.utc)


class SessionStatus(StrEnum):
    ACTIVE = "ACTIVE"
    CLOSED = "CLOSED"


@dataclass(slots=True)
class CameraSessionContext:
    camera_id: str
    session_id: str
    opened_at: datetime = field(default_factory=utc_now)
    last_heartbeat_at: datetime = field(default_factory=utc_now)
    last_activity_at: datetime = field(default_factory=utc_now)
    status: SessionStatus = SessionStatus.ACTIVE
    closed_at: datetime | None = None
    frame_count: int = 0
    dropped_frame_count: int = 0

    def heartbeat(self) -> None:
        if self.status is SessionStatus.CLOSED:
            raise ValueError("Closed session cannot receive heartbeat")

        self.last_heartbeat_at = utc_now()
        self.last_activity_at = self.last_heartbeat_at

    def register_frame(self) -> None:
        if self.status is SessionStatus.CLOSED:
            raise ValueError("Closed session cannot receive frames")

        self.frame_count += 1
        self.last_activity_at = utc_now()

    def register_dropped_frame(self) -> None:
        self.dropped_frame_count += 1

    def close(self) -> None:
        if self.status is SessionStatus.CLOSED:
            return

        now = utc_now()
        self.status = SessionStatus.CLOSED
        self.closed_at = now
        self.last_heartbeat_at = now
        self.last_activity_at = now