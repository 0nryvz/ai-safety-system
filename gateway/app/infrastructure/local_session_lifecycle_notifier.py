from datetime import datetime


class LocalCameraSessionLifecycleNotifier:
    async def notify_open(
            self,
            camera_id: str,
            session_id: str,
            opened_at: datetime,
    ) -> None:
        return None

    async def notify_heartbeat(
            self,
            camera_id: str,
            session_id: str,
            heartbeat_at: datetime,
    ) -> None:
        return None

    async def notify_close(
            self,
            camera_id: str,
            session_id: str,
            closed_at: datetime,
    ) -> None:
        return None