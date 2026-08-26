from datetime import datetime

from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_camera_session_lifecycle_notifier,
    get_session_manager,
)
from app.main import app
from app.services.session_manager import SessionManager


class RecordingSessionLifecycleNotifier:
    def __init__(self) -> None:
        self.heartbeat_calls: list[
            tuple[str, str, datetime]
        ] = []

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
        self.heartbeat_calls.append(
            (
                camera_id,
                session_id,
                heartbeat_at,
            )
        )

    async def notify_close(
            self,
            camera_id: str,
            session_id: str,
            closed_at: datetime,
    ) -> None:
        return None


def test_heartbeat_updates_active_session() -> None:
    session_manager = SessionManager()
    lifecycle_notifier = RecordingSessionLifecycleNotifier()

    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )
    app.dependency_overrides[
        get_camera_session_lifecycle_notifier
    ] = lambda: lifecycle_notifier

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

            previous_heartbeat = open_response.json()[
                "session"
            ]["lastHeartbeatAt"]

            heartbeat_response = client.post(
                "/api/v1/sessions/session-1/heartbeat",
                json={
                    "cameraId": "camera-1",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert open_response.status_code == 201
    assert heartbeat_response.status_code == 200

    body = heartbeat_response.json()

    assert body["cameraId"] == "camera-1"
    assert body["sessionId"] == "session-1"
    assert body["status"] == "ACTIVE"
    assert body["lastHeartbeatAt"] >= previous_heartbeat

    assert len(lifecycle_notifier.heartbeat_calls) == 1

    camera_id, session_id, heartbeat_at = (
        lifecycle_notifier.heartbeat_calls[0]
    )

    assert camera_id == "camera-1"
    assert session_id == "session-1"
    assert heartbeat_at == datetime.fromisoformat(
        body["lastHeartbeatAt"],
    )

def test_heartbeat_rejects_missing_session() -> None:
    session_manager = SessionManager()
    lifecycle_notifier = RecordingSessionLifecycleNotifier()

    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )
    app.dependency_overrides[
        get_camera_session_lifecycle_notifier
    ] = lambda: lifecycle_notifier

    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/sessions/missing-session/heartbeat",
                json={
                    "cameraId": "camera-1",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 404
    assert response.json() == {
        "detail": "SESSION_NOT_FOUND",
    }

    assert lifecycle_notifier.heartbeat_calls == []

def test_heartbeat_rejects_session_owned_by_another_camera() -> None:
    session_manager = SessionManager()
    lifecycle_notifier = RecordingSessionLifecycleNotifier()

    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )
    app.dependency_overrides[
        get_camera_session_lifecycle_notifier
    ] = lambda: lifecycle_notifier

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

            heartbeat_response = client.post(
                "/api/v1/sessions/session-1/heartbeat",
                json={
                    "cameraId": "camera-2",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert open_response.status_code == 201

    assert heartbeat_response.status_code == 409
    assert heartbeat_response.json() == {
        "detail": "SESSION_CONFLICT",
    }

    assert lifecycle_notifier.heartbeat_calls == []