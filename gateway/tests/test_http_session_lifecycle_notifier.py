import json
from datetime import datetime, timezone

import httpx
import pytest

from app.infrastructure.http_session_lifecycle_notifier import (
    HttpCameraSessionLifecycleNotifier,
)
from app.services.session_lifecycle_notifier import (
    CameraSessionLifecycleNotificationError,
)


def build_client(
        handler,
) -> HttpCameraSessionLifecycleNotifier:
    transport = httpx.MockTransport(handler)

    http_client = httpx.AsyncClient(
        transport=transport,
    )

    return HttpCameraSessionLifecycleNotifier(
        backend_base_url=(
            "http://backend.internal:8080"
        ),
        internal_api_key="test-internal-key",
        http_client=http_client,
    )


@pytest.mark.asyncio
async def test_open_sends_backend_request() -> None:
    captured_request = None

    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        nonlocal captured_request
        captured_request = request

        return httpx.Response(
            status_code=200,
        )

    client = build_client(handler)

    await client.notify_open(
        camera_id=(
            "11111111-1111-1111-1111-111111111111"
        ),
        session_id=(
            "22222222-2222-2222-2222-222222222222"
        ),
        opened_at=datetime.now(timezone.utc),
    )

    assert captured_request is not None

    assert str(captured_request.url) == (
        "http://backend.internal:8080"
        "/internal/v1/camera-sessions/open"
    )

    assert (
            captured_request.headers[
                "X-Internal-Api-Key"
            ]
            == "test-internal-key"
    )

    assert json.loads(
        captured_request.content.decode("utf-8")
    ) == {
               "cameraId": (
                   "11111111-1111-1111-1111-111111111111"
               ),
               "sessionId": (
                   "22222222-2222-2222-2222-222222222222"
               ),
           }


@pytest.mark.asyncio
async def test_heartbeat_uses_heartbeat_endpoint(
) -> None:
    captured_url = None

    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        nonlocal captured_url
        captured_url = str(request.url)

        return httpx.Response(
            status_code=200,
        )

    client = build_client(handler)

    await client.notify_heartbeat(
        camera_id=(
            "11111111-1111-1111-1111-111111111111"
        ),
        session_id=(
            "22222222-2222-2222-2222-222222222222"
        ),
        heartbeat_at=datetime.now(timezone.utc),
    )

    assert captured_url == (
        "http://backend.internal:8080"
        "/internal/v1/camera-sessions/heartbeat"
    )


@pytest.mark.asyncio
async def test_close_uses_close_endpoint() -> None:
    captured_url = None

    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        nonlocal captured_url
        captured_url = str(request.url)

        return httpx.Response(
            status_code=200,
        )

    client = build_client(handler)

    await client.notify_close(
        camera_id=(
            "11111111-1111-1111-1111-111111111111"
        ),
        session_id=(
            "22222222-2222-2222-2222-222222222222"
        ),
        closed_at=datetime.now(timezone.utc),
    )

    assert captured_url == (
        "http://backend.internal:8080"
        "/internal/v1/camera-sessions/close"
    )


@pytest.mark.asyncio
async def test_4xx_is_not_retryable() -> None:
    client = build_client(
        lambda request: httpx.Response(
            status_code=401,
        )
    )

    with pytest.raises(
            CameraSessionLifecycleNotificationError
    ) as exc_info:
        await client.notify_open(
            camera_id=(
                "11111111-1111-1111-1111-111111111111"
            ),
            session_id=(
                "22222222-2222-2222-2222-222222222222"
            ),
            opened_at=datetime.now(timezone.utc),
        )

    assert exc_info.value.retryable is False