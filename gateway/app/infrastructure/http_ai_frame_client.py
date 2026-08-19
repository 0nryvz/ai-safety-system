from datetime import timezone

import httpx

from app.domain.frame import FramePacket


class AIFrameHttpError(RuntimeError):
    def __init__(
            self,
            message: str,
            *,
            retryable: bool,
    ) -> None:
        super().__init__(message)
        self.retryable = retryable


class HttpAIFrameClient:
    def __init__(
            self,
            *,
            ai_base_url: str,
            http_client: httpx.AsyncClient | None = None,
            timeout_seconds: float = 1.0,
    ) -> None:
        self._endpoint = (
            f"{ai_base_url.rstrip('/')}"
            "/internal/v1/inference/frames"
        )

        if http_client is None:
            self._http_client = httpx.AsyncClient(
                timeout=timeout_seconds,
            )
        else:
            self._http_client = http_client

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        timestamp = (
            frame.captured_at
            .astimezone(timezone.utc)
            .isoformat()
            .replace("+00:00", "Z")
        )

        try:
            response = await self._http_client.post(
                self._endpoint,
                content=frame.data,
                headers={
                    "Content-Type": frame.content_type,
                    "X-Camera-Id": frame.camera_id,
                    "X-Session-Id": frame.session_id,
                    "X-Frame-Timestamp": timestamp,
                    "X-Frame-Event-Id": frame.event_id,
                },
            )
        except httpx.TimeoutException as exc:
            raise AIFrameHttpError(
                "AI frame request timed out",
                retryable=True,
            ) from exc
        except httpx.RequestError as exc:
            raise AIFrameHttpError(
                "AI frame request failed",
                retryable=True,
            ) from exc

        if response.status_code in {200, 202}:
            return

        raise AIFrameHttpError(
            "AI frame request failed "
            f"with status={response.status_code}",
            retryable=(
                    response.status_code >= 500
            ),
        )