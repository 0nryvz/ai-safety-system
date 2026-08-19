import httpx
import pytest
from datetime import datetime, timezone

from app.domain.frame import FramePacket
from app.infrastructure.http_ai_frame_client import (
    AIFrameHttpError,
    HttpAIFrameClient,
)


def create_client(
        handler,
) -> HttpAIFrameClient:
    transport = httpx.MockTransport(handler)

    http_client = httpx.AsyncClient(
        transport=transport,
    )

    return HttpAIFrameClient(
        ai_base_url="http://ai.internal:8001",
        http_client=http_client,
    )


@pytest.mark.asyncio
async def test_send_frame_posts_expected_contract(
) -> None:
    captured_request = None

    def handler(
            request: httpx.Request,
    ) -> httpx.Response:
        nonlocal captured_request
        captured_request = request

        return httpx.Response(
            status_code=202,
        )

    client = create_client(handler)

    frame = FramePacket(
        camera_id="camera-1",
        session_id="session-1",
        captured_at=datetime(
            2026,
            8,
            19,
            2,
            30,
            0,
            tzinfo=timezone.utc,
        ),
        content_type="image/jpeg",
        data=b"jpeg-data",
        event_id="event-123",
    )

    await client.send_frame(frame)

    assert captured_request is not None

    assert str(captured_request.url) == (
        "http://ai.internal:8001"
        "/internal/v1/inference/frames"
    )

    assert (
            captured_request.headers["Content-Type"]
            == "image/jpeg"
    )

    assert (
            captured_request.headers["X-Camera-Id"]
            == "camera-1"
    )

    assert (
            captured_request.headers["X-Session-Id"]
            == "session-1"
    )

    assert (
            captured_request.headers["X-Frame-Timestamp"]
            == "2026-08-19T02:30:00Z"
    )

    assert (
            captured_request.headers["X-Frame-Event-Id"]
            == "event-123"
    )

    assert captured_request.content == b"jpeg-data"


@pytest.mark.asyncio
async def test_200_response_is_success() -> None:
    client = create_client(
        lambda request: httpx.Response(
            status_code=200,
        )
    )

    frame = FramePacket(
        camera_id="camera-1",
        session_id="session-1",
        captured_at=datetime.now(timezone.utc),
        content_type="image/jpeg",
        data=b"jpeg-data",
    )

    await client.send_frame(frame)


@pytest.mark.asyncio
async def test_400_is_not_retryable() -> None:
    client = create_client(
        lambda request: httpx.Response(
            status_code=400,
        )
    )

    frame = FramePacket(
        camera_id="camera-1",
        session_id="session-1",
        captured_at=datetime.now(timezone.utc),
        content_type="image/jpeg",
        data=b"jpeg-data",
    )

    with pytest.raises(AIFrameHttpError) as exc_info:
        await client.send_frame(frame)

    assert exc_info.value.retryable is False


@pytest.mark.asyncio
async def test_503_is_retryable() -> None:
    client = create_client(
        lambda request: httpx.Response(
            status_code=503,
        )
    )

    frame = FramePacket(
        camera_id="camera-1",
        session_id="session-1",
        captured_at=datetime.now(timezone.utc),
        content_type="image/jpeg",
        data=b"jpeg-data",
    )

    with pytest.raises(AIFrameHttpError) as exc_info:
        await client.send_frame(frame)

    assert exc_info.value.retryable is True