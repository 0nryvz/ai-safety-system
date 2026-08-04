from datetime import datetime, timezone

from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_session_frame_queue_manager,
    get_session_manager,
)
from app.main import app
from app.services.session_frame_queue_manager import (
    SessionFrameQueueManager,
)
from app.services.session_manager import SessionManager


def test_metrics_returns_gateway_state() -> None:
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

    jpeg_data = b"\xff\xd8frame\xff\xd9"

    try:
        with TestClient(app) as client:
            open_response = client.post(
                "/api/v1/sessions/open",
                json={
                    "cameraId": "camera-1",
                    "sessionId": "session-1",
                    "sessionToken": "dev-session-token",
                },
            )

            upload_response = client.post(
                "/api/v1/sessions/session-1/frames",
                headers={
                    "X-Camera-Id": "camera-1",
                    "X-Frame-Timestamp": (
                        datetime.now(timezone.utc).isoformat()
                    ),
                    "Content-Type": "image/jpeg",
                },
                content=jpeg_data,
            )

            metrics_response = client.get("/metrics")
    finally:
        app.dependency_overrides.clear()

    assert open_response.status_code == 201
    assert upload_response.status_code == 202
    assert metrics_response.status_code == 200

    body = metrics_response.json()

    assert body["active_sessions"] == 1
    assert body["active_frame_queues"] == 1
    assert body["queued_frames"] == 1
    assert body["frame_queue_capacity_per_session"] >= 1
    assert "timestamp" in body