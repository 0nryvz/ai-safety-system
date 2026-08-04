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
        self.close_calls: list[
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
        return None

    async def notify_close(
            self,
            camera_id: str,
            session_id: str,
            closed_at: datetime,
    ) -> None:
        self.close_calls.append(
            (
                camera_id,
                session_id,
                closed_at,
            )
        )


def test_close_session_removes_active_session_and_notifies() -> None:
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

            close_response = client.post(
                "/api/v1/sessions/session-1/close",
                json={
                    "cameraId": "camera-1",
                },
            )

            heartbeat_response = client.post(
                "/api/v1/sessions/session-1/heartbeat",
                json={
                    "cameraId": "camera-1",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert open_response.status_code == 201

    assert close_response.status_code == 204
    assert close_response.content == b""

    assert heartbeat_response.status_code == 404
    assert heartbeat_response.json() == {
        "detail": "SESSION_NOT_FOUND",
    }

    assert len(lifecycle_notifier.close_calls) == 1

    camera_id, session_id, closed_at = (
        lifecycle_notifier.close_calls[0]
    )

    assert camera_id == "camera-1"
    assert session_id == "session-1"
    assert closed_at.tzinfo is not None

def test_close_session_is_idempotent() -> None:
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

            first_close_response = client.post(
                "/api/v1/sessions/session-1/close",
                json={
                    "cameraId": "camera-1",
                },
            )

            second_close_response = client.post(
                "/api/v1/sessions/session-1/close",
                json={
                    "cameraId": "camera-1",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert open_response.status_code == 201

    assert first_close_response.status_code == 204
    assert second_close_response.status_code == 204

    assert len(lifecycle_notifier.close_calls) == 1

def test_close_session_rejects_session_owned_by_another_camera() -> None:
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

            close_response = client.post(
                "/api/v1/sessions/session-1/close",
                json={
                    "cameraId": "camera-2",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert open_response.status_code == 201

    assert close_response.status_code == 409
    assert close_response.json() == {
        "detail": "SESSION_CONFLICT",
    }

    assert lifecycle_notifier.close_calls == []