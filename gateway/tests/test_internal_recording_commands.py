import asyncio

from fastapi.testclient import TestClient

from app.api.dependencies import get_session_manager
from app.main import app
from app.services.session_manager import SessionManager


def _open_session(
        session_manager: SessionManager,
        camera_id: str,
        session_id: str,
) -> None:
    asyncio.run(
        session_manager.open_session(
            camera_id=camera_id,
            session_id=session_id,
        )
    )


def _start_payload(
        *,
        command_id: str,
        recording_id: str,
        violation_id: str,
        camera_id: str,
        session_id: str,
) -> dict:
    return {
        "commandId": command_id,
        "recordingId": recording_id,
        "violationId": violation_id,
        "cameraId": camera_id,
        "sessionId": session_id,
        "startedAt": "2026-08-11T10:00:00Z",
        "preBufferSeconds": 5,
        "postBufferSeconds": 3,
        "maxClipSeconds": 30,
    }


def _stop_payload(
        *,
        command_id: str,
        violation_id: str,
) -> dict:
    return {
        "commandId": command_id,
        "violationId": violation_id,
        "endedAt": "2026-08-11T10:00:30Z",
    }


def test_accepts_first_start_command() -> None:
    session_manager = SessionManager()
    _open_session(session_manager, "camera-1", "session-1")

    app.dependency_overrides[get_session_manager] = lambda: session_manager

    try:
        with TestClient(app) as client:
            response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-1",
                    recording_id="recording-1",
                    violation_id="violation-1",
                    camera_id="camera-1",
                    session_id="session-1",
                ),
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 202
    assert response.json() == {
        "commandId": "start-1",
        "violationId": "violation-1",
        "idempotent": False,
    }


def test_duplicate_start_with_same_command_id_is_idempotent() -> None:
    session_manager = SessionManager()
    _open_session(session_manager, "camera-1", "session-1")

    app.dependency_overrides[get_session_manager] = lambda: session_manager

    payload = _start_payload(
        command_id="start-1",
        recording_id="recording-1",
        violation_id="violation-1",
        camera_id="camera-1",
        session_id="session-1",
    )

    try:
        with TestClient(app) as client:
            first_response = client.post(
                "/internal/v1/recordings/commands/start",
                json=payload,
            )
            second_response = client.post(
                "/internal/v1/recordings/commands/start",
                json=payload,
            )
    finally:
        app.dependency_overrides.clear()

    assert first_response.status_code == 202
    assert first_response.json()["idempotent"] is False

    assert second_response.status_code == 202
    assert second_response.json()["idempotent"] is True


def test_duplicate_start_with_same_command_id_but_different_recording_id_conflicts() -> None:
    session_manager = SessionManager()
    _open_session(session_manager, "camera-1", "session-1")

    app.dependency_overrides[get_session_manager] = lambda: session_manager

    try:
        with TestClient(app) as client:
            first_response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-1",
                    recording_id="recording-1",
                    violation_id="violation-1",
                    camera_id="camera-1",
                    session_id="session-1",
                ),
            )
            conflict_response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-1",
                    recording_id="recording-2",
                    violation_id="violation-1",
                    camera_id="camera-1",
                    session_id="session-1",
                ),
            )
    finally:
        app.dependency_overrides.clear()

    assert first_response.status_code == 202
    assert conflict_response.status_code == 409
    assert conflict_response.json()["detail"] == "RECORDING_START_CONFLICT"


def test_start_conflicts_for_same_violation_with_different_command_id() -> None:
    session_manager = SessionManager()
    _open_session(session_manager, "camera-1", "session-1")

    app.dependency_overrides[get_session_manager] = lambda: session_manager

    try:
        with TestClient(app) as client:
            first_response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-1",
                    recording_id="recording-1",
                    violation_id="violation-1",
                    camera_id="camera-1",
                    session_id="session-1",
                ),
            )
            conflict_response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-2",
                    recording_id="recording-2",
                    violation_id="violation-1",
                    camera_id="camera-1",
                    session_id="session-1",
                ),
            )
    finally:
        app.dependency_overrides.clear()

    assert first_response.status_code == 202
    assert conflict_response.status_code == 409
    assert conflict_response.json()["detail"] == "RECORDING_START_CONFLICT"


def test_state_isolated_across_different_violations_and_sessions() -> None:
    session_manager = SessionManager()
    _open_session(session_manager, "camera-1", "session-1")
    _open_session(session_manager, "camera-2", "session-2")

    app.dependency_overrides[get_session_manager] = lambda: session_manager

    try:
        with TestClient(app) as client:
            first_response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-1",
                    recording_id="recording-1",
                    violation_id="violation-1",
                    camera_id="camera-1",
                    session_id="session-1",
                ),
            )
            second_response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-2",
                    recording_id="recording-2",
                    violation_id="violation-2",
                    camera_id="camera-2",
                    session_id="session-2",
                ),
            )
    finally:
        app.dependency_overrides.clear()

    assert first_response.status_code == 202
    assert second_response.status_code == 202


def test_start_returns_not_found_when_session_missing() -> None:
    app.dependency_overrides[get_session_manager] = lambda: SessionManager()

    try:
        with TestClient(app) as client:
            response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-1",
                    recording_id="recording-1",
                    violation_id="violation-1",
                    camera_id="camera-1",
                    session_id="session-missing",
                ),
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 404
    assert response.json()["detail"] == "SESSION_NOT_FOUND"


def test_start_returns_conflict_when_camera_session_mismatch() -> None:
    session_manager = SessionManager()
    _open_session(session_manager, "camera-1", "session-1")

    app.dependency_overrides[get_session_manager] = lambda: session_manager

    try:
        with TestClient(app) as client:
            response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-1",
                    recording_id="recording-1",
                    violation_id="violation-1",
                    camera_id="camera-2",
                    session_id="session-1",
                ),
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 409
    assert response.json()["detail"] == "SESSION_CONFLICT"


def test_accepts_stop_command_after_start() -> None:
    session_manager = SessionManager()
    _open_session(session_manager, "camera-1", "session-1")

    app.dependency_overrides[get_session_manager] = lambda: session_manager

    try:
        with TestClient(app) as client:
            start_response = client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-1",
                    recording_id="recording-1",
                    violation_id="violation-1",
                    camera_id="camera-1",
                    session_id="session-1",
                ),
            )
            stop_response = client.post(
                "/internal/v1/recordings/commands/stop",
                json=_stop_payload(
                    command_id="stop-1",
                    violation_id="violation-1",
                ),
            )
    finally:
        app.dependency_overrides.clear()

    assert start_response.status_code == 202
    assert stop_response.status_code == 202
    assert stop_response.json() == {
        "commandId": "stop-1",
        "violationId": "violation-1",
        "idempotent": False,
    }


def test_duplicate_stop_with_same_command_id_is_idempotent() -> None:
    session_manager = SessionManager()
    _open_session(session_manager, "camera-1", "session-1")

    app.dependency_overrides[get_session_manager] = lambda: session_manager

    try:
        with TestClient(app) as client:
            client.post(
                "/internal/v1/recordings/commands/start",
                json=_start_payload(
                    command_id="start-1",
                    recording_id="recording-1",
                    violation_id="violation-1",
                    camera_id="camera-1",
                    session_id="session-1",
                ),
            )

            first_stop = client.post(
                "/internal/v1/recordings/commands/stop",
                json=_stop_payload(
                    command_id="stop-1",
                    violation_id="violation-1",
                ),
            )
            second_stop = client.post(
                "/internal/v1/recordings/commands/stop",
                json=_stop_payload(
                    command_id="stop-1",
                    violation_id="violation-1",
                ),
            )
    finally:
        app.dependency_overrides.clear()

    assert first_stop.status_code == 202
    assert first_stop.json()["idempotent"] is False

    assert second_stop.status_code == 202
    assert second_stop.json()["idempotent"] is True


def test_stop_without_start_returns_controlled_error() -> None:
    with TestClient(app) as client:
        response = client.post(
            "/internal/v1/recordings/commands/stop",
            json=_stop_payload(
                command_id="stop-1",
                violation_id="violation-1",
            ),
        )

    assert response.status_code == 404
    assert response.json()["detail"] == "RECORDING_NOT_FOUND_FOR_VIOLATION"


def test_endpoint_rejects_non_utc_timestamp_payload() -> None:
    session_manager = SessionManager()
    _open_session(session_manager, "camera-1", "session-1")
    app.dependency_overrides[get_session_manager] = lambda: session_manager

    try:
        with TestClient(app) as client:
            response = client.post(
                "/internal/v1/recordings/commands/start",
                json={
                    "commandId": "start-1",
                    "recordingId": "recording-1",
                    "violationId": "violation-1",
                    "cameraId": "camera-1",
                    "sessionId": "session-1",
                    "startedAt": "2026-08-11T13:00:00+03:00",
                    "preBufferSeconds": 5,
                    "postBufferSeconds": 3,
                    "maxClipSeconds": 30,
                },
            )
    finally:
        app.dependency_overrides.clear()

    assert response.status_code == 422
