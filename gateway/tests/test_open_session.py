from fastapi.testclient import TestClient

from app.api.dependencies import (
    get_camera_session_validator,
    get_session_manager,
)
from app.main import app
from app.services.session_manager import SessionManager
from app.services.session_validator import SessionValidationResult


class AcceptingSessionValidator:
    async def validate_open_session(
            self,
            camera_id: str,
            session_id: str,
            session_token: str,
    ) -> SessionValidationResult:
        return SessionValidationResult(
            is_valid=True,
            camera_active=True,
        )

class InvalidTokenSessionValidator:
    async def validate_open_session(
            self,
            camera_id: str,
            session_id: str,
            session_token: str,
    ) -> SessionValidationResult:
        return SessionValidationResult(
            is_valid=False,
            camera_active=False,
            reason="INVALID_SESSION_TOKEN",
        )

class InactiveCameraSessionValidator:
    async def validate_open_session(
            self,
            camera_id: str,
            session_id: str,
            session_token: str,
    ) -> SessionValidationResult:
        return SessionValidationResult(
            is_valid=True,
            camera_active=False,
            reason="CAMERA_INACTIVE",
        )

def test_open_session_creates_new_session() -> None:
    session_manager = SessionManager()

    app.dependency_overrides[get_camera_session_validator] = (
        lambda: AcceptingSessionValidator()
    )
    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )

    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/sessions/open",
                json={
                    "cameraId": "camera-1",
                    "sessionId": "session-1",
                    "sessionToken": "valid-token",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 201

    body = response.json()

    assert body["created"] is True
    assert body["session"]["cameraId"] == "camera-1"
    assert body["session"]["sessionId"] == "session-1"
    assert body["session"]["status"] == "ACTIVE"
    assert body["session"]["frameCount"] == 0
    assert body["session"]["droppedFrameCount"] == 0
    assert "openedAt" in body["session"]
    assert "lastHeartbeatAt" in body["session"]

def test_open_session_reconnects_existing_session() -> None:
    session_manager = SessionManager()

    app.dependency_overrides[get_camera_session_validator] = (
        lambda: AcceptingSessionValidator()
    )
    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )

    request_body = {
        "cameraId": "camera-1",
        "sessionId": "session-1",
        "sessionToken": "valid-token",
    }

    try:
        with TestClient(app) as client:
            first_response = client.post(
                "/api/v1/sessions/open",
                json=request_body,
            )

            second_response = client.post(
                "/api/v1/sessions/open",
                json=request_body,
            )
    finally:
        app.dependency_overrides.clear()

    assert first_response.status_code == 201
    assert first_response.json()["created"] is True

    assert second_response.status_code == 200

    second_body = second_response.json()

    assert second_body["created"] is False
    assert second_body["session"]["cameraId"] == "camera-1"
    assert second_body["session"]["sessionId"] == "session-1"
    assert second_body["session"]["status"] == "ACTIVE"

def test_open_session_rejects_invalid_token() -> None:
    session_manager = SessionManager()

    app.dependency_overrides[get_camera_session_validator] = (
        lambda: InvalidTokenSessionValidator()
    )
    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )

    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/sessions/open",
                json={
                    "cameraId": "camera-1",
                    "sessionId": "session-1",
                    "sessionToken": "invalid-token",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 401
    assert response.json() == {
        "detail": "INVALID_SESSION_TOKEN",
    }

def test_open_session_rejects_session_id_owned_by_another_camera() -> None:
    session_manager = SessionManager()

    app.dependency_overrides[get_camera_session_validator] = (
        lambda: AcceptingSessionValidator()
    )
    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )

    try:
        with TestClient(app) as client:
            first_response = client.post(
                "/api/v1/sessions/open",
                json={
                    "cameraId": "camera-1",
                    "sessionId": "session-1",
                    "sessionToken": "valid-token",
                },
            )

            conflict_response = client.post(
                "/api/v1/sessions/open",
                json={
                    "cameraId": "camera-2",
                    "sessionId": "session-1",
                    "sessionToken": "valid-token",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert first_response.status_code == 201

    assert conflict_response.status_code == 409
    assert conflict_response.json() == {
        "detail": "SESSION_CONFLICT",
    }

def test_open_session_rejects_invalid_request_body() -> None:
    session_manager = SessionManager()

    app.dependency_overrides[get_camera_session_validator] = (
        lambda: AcceptingSessionValidator()
    )
    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )

    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/sessions/open",
                json={
                    "cameraId": "camera-1",
                    "sessionId": "",
                    "sessionToken": "valid-token",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 422

    validation_errors = response.json()["detail"]

    assert any(
        error["loc"] == ["body", "sessionId"]
        for error in validation_errors
    )

def test_open_session_rejects_inactive_camera() -> None:
    session_manager = SessionManager()

    app.dependency_overrides[get_camera_session_validator] = (
        lambda: InactiveCameraSessionValidator()
    )
    app.dependency_overrides[get_session_manager] = (
        lambda: session_manager
    )

    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/sessions/open",
                json={
                    "cameraId": "camera-1",
                    "sessionId": "session-1",
                    "sessionToken": "valid-token",
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 403
    assert response.json() == {
        "detail": "CAMERA_INACTIVE",
    }