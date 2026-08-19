from datetime import datetime
from typing import Protocol


class CameraSessionLifecycleNotificationError(
    RuntimeError
):
    def __init__(
            self,
            message: str,
            *,
            retryable: bool,
    ) -> None:
        super().__init__(message)
        self.retryable = retryable

class CameraSessionLifecycleNotifier(Protocol):
    async def notify_open(
            self,
            camera_id: str,
            session_id: str,
            opened_at: datetime,
    ) -> None:
        ...

    async def notify_heartbeat(
            self,
            camera_id: str,
            session_id: str,
            heartbeat_at: datetime,
    ) -> None:
        ...

    async def notify_close(
            self,
            camera_id: str,
            session_id: str,
            closed_at: datetime,
    ) -> None:
        ...