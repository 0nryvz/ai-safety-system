from datetime import datetime, timezone

import pytest
from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_session_frame_queue_manager,
    get_session_manager,
)
from app.core.config import Settings, get_settings
from app.main import app
from app.services.session_frame_queue_manager import (
    SessionFrameQueueManager,
)
from app.services.session_manager import SessionManager


def jpeg(payload: bytes = b"frame") -> bytes:
    return b"\xff\xd8" + payload + b"\xff\xd9"


def open_session(client: TestClient) -> None:
    response = client.post(
        "/api/v1/sessions/open",
        json={
            "cameraId": "camera-1",
            "sessionId": "session-1",
            "sessionToken": "dev-session-token",
        },
    )

    assert response.status_code == 201


def post_frame(
        client: TestClient,
        *,
        camera_id: str = "camera-1",
        session_id: str = "session-1",
        data: bytes | None = None,
        content_type: str = "image/jpeg",
        timestamp: str | None = None,
):
    headers = {
        "X-Camera-Id": camera_id,
        "Content-Type": content_type,
    }

    if timestamp is not None:
        headers["X-Frame-Timestamp"] = timestamp
    else:
        headers["X-Frame-Timestamp"] = (
            datetime.now(timezone.utc).isoformat()
        )

    return client.post(
        f"/api/v1/sessions/{session_id}/frames",
        headers=headers,
        content=data if data is not None else jpeg(),
    )


@pytest.fixture
def gateway_state():
    session_manager = SessionManager()
    queue_manager = SessionFrameQueueManager(
        max_frames=2,
    )

    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )
    app.dependency_overrides[
        get_session_frame_queue_manager
    ] = lambda: queue_manager

    yield session_manager, queue_manager

    app.dependency_overrides.clear()


def test_upload_frame_accepts_jpeg(
        gateway_state,
) -> None:
    with TestClient(app) as client:
        open_session(client)

        response = post_frame(
            client,
            data=jpeg(b"first"),
        )

    assert response.status_code == 202

    body = response.json()

    assert body["accepted"] is True
    assert body["cameraId"] == "camera-1"
    assert body["sessionId"] == "session-1"
    assert body["queueDepth"] == 1
    assert body["queueCapacity"] == 2
    assert body["frameCount"] == 1
    assert body["droppedFrameCount"] == 0
    assert body["sizeBytes"] == len(jpeg(b"first"))


def test_full_queue_drops_oldest_frame(
        gateway_state,
) -> None:
    with TestClient(app) as client:
        open_session(client)

        first_response = post_frame(
            client,
            data=jpeg(b"first"),
        )
        second_response = post_frame(
            client,
            data=jpeg(b"second"),
        )
        third_response = post_frame(
            client,
            data=jpeg(b"third"),
        )

    assert first_response.status_code == 202
    assert second_response.status_code == 202
    assert third_response.status_code == 202

    body = third_response.json()

    assert body["queueDepth"] == 2
    assert body["queueCapacity"] == 2
    assert body["frameCount"] == 3
    assert body["droppedFrameCount"] == 1


def test_upload_frame_rejects_unsupported_content_type(
        gateway_state,
) -> None:
    with TestClient(app) as client:
        open_session(client)

        response = post_frame(
            client,
            content_type="image/png",
            data=b"png-data",
        )

    assert response.status_code == 415
    assert response.json() == {
        "detail": "UNSUPPORTED_FRAME_CONTENT_TYPE",
    }


def test_upload_frame_rejects_invalid_jpeg(
        gateway_state,
) -> None:
    with TestClient(app) as client:
        open_session(client)

        response = post_frame(
            client,
            data=b"not-a-jpeg",
        )

    assert response.status_code == 422
    assert response.json() == {
        "detail": "INVALID_JPEG_FRAME",
    }


def test_upload_frame_rejects_oversized_frame(
        gateway_state,
) -> None:
    app.dependency_overrides[get_settings] = lambda: Settings(
        frame_queue_max_frames=2,
        frame_max_bytes=8,
    )

    with TestClient(app) as client:
        open_session(client)

        response = post_frame(
            client,
            data=jpeg(b"12345678"),
        )

    assert response.status_code == 413
    assert response.json() == {
        "detail": "FRAME_TOO_LARGE",
    }


def test_upload_frame_rejects_missing_session(
        gateway_state,
) -> None:
    with TestClient(app) as client:
        response = post_frame(
            client,
            session_id="missing-session",
        )

    assert response.status_code == 404
    assert response.json() == {
        "detail": "SESSION_NOT_FOUND",
    }


def test_upload_frame_rejects_another_camera(
        gateway_state,
) -> None:
    with TestClient(app) as client:
        open_session(client)

        response = post_frame(
            client,
            camera_id="camera-2",
        )

    assert response.status_code == 409
    assert response.json() == {
        "detail": "SESSION_CONFLICT",
    }


def test_upload_frame_requires_timestamp_header(
        gateway_state,
) -> None:
    with TestClient(app) as client:
        open_session(client)

        response = client.post(
            "/api/v1/sessions/session-1/frames",
            headers={
                "X-Camera-Id": "camera-1",
                "Content-Type": "image/jpeg",
            },
            content=jpeg(),
        )

    assert response.status_code == 422


def test_close_session_cleans_frame_queue(
        gateway_state,
) -> None:
    with TestClient(app) as client:
        open_session(client)

        upload_response = post_frame(client)

        close_response = client.post(
            "/api/v1/sessions/session-1/close",
            json={
                "cameraId": "camera-1",
            },
        )

        second_upload_response = post_frame(client)

    assert upload_response.status_code == 202
    assert close_response.status_code == 204

    assert second_upload_response.status_code == 404
    assert second_upload_response.json() == {
        "detail": "SESSION_NOT_FOUND",
    }