from datetime import datetime

import httpx

from app.services.session_lifecycle_notifier import (
    CameraSessionLifecycleNotificationError,
)


class HttpCameraSessionLifecycleNotifier:
    def __init__(
            self,
            *,
            backend_base_url: str,
            internal_api_key: str,
            timeout_seconds: float = 5.0,
            http_client: httpx.AsyncClient | None = None,
    ) -> None:
        self._internal_api_key = internal_api_key

        self._base_url = (
            f"{backend_base_url.rstrip('/')}"
            "/internal/v1/camera-sessions"
        )

        if http_client is None:
            self._http_client = httpx.AsyncClient(
                timeout=timeout_seconds,
            )
            return

        self._http_client = http_client

    async def notify_open(
            self,
            camera_id: str,
            session_id: str,
            opened_at: datetime,
    ) -> None:
        await self._notify(
            action="open",
            camera_id=camera_id,
            session_id=session_id,
        )

    async def notify_heartbeat(
            self,
            camera_id: str,
            session_id: str,
            heartbeat_at: datetime,
    ) -> None:
        await self._notify(
            action="heartbeat",
            camera_id=camera_id,
            session_id=session_id,
        )

    async def notify_close(
            self,
            camera_id: str,
            session_id: str,
            closed_at: datetime,
    ) -> None:
        await self._notify(
            action="close",
            camera_id=camera_id,
            session_id=session_id,
        )

    async def _notify(
            self,
            *,
            action: str,
            camera_id: str,
            session_id: str,
    ) -> None:
        try:
            response = await self._http_client.post(
                f"{self._base_url}/{action}",
                json={
                    "cameraId": camera_id,
                    "sessionId": session_id,
                },
                headers={
                    "X-Internal-Api-Key": (
                        self._internal_api_key
                    ),
                },
            )
        except httpx.TimeoutException as exc:
            raise (
                CameraSessionLifecycleNotificationError(
                    "Camera session lifecycle request timed out",
                    retryable=True,
                )
            ) from exc
        except httpx.RequestError as exc:
            raise (
                CameraSessionLifecycleNotificationError(
                    "Camera session lifecycle request failed",
                    retryable=True,
                )
            ) from exc

        if 200 <= response.status_code < 300:
            return

        raise CameraSessionLifecycleNotificationError(
            "Camera session lifecycle request failed "
            f"with status={response.status_code}",
            retryable=(
                    response.status_code >= 500
            ),
        )