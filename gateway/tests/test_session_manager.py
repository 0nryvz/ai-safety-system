import pytest

from app.domain.session import SessionStatus
from app.services.session_manager import (
    SessionConflictError,
    SessionManager,
    SessionNotFoundError,
)


@pytest.mark.asyncio
async def test_open_session_creates_new_session() -> None:
    manager = SessionManager()

    session, created = await manager.open_session(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert created is True
    assert session.camera_id == "camera-1"
    assert session.session_id == "session-1"
    assert session.status is SessionStatus.ACTIVE
    assert await manager.active_session_count() == 1


@pytest.mark.asyncio
async def test_reconnect_returns_existing_session() -> None:
    manager = SessionManager()

    first_session, first_created = await manager.open_session(
        camera_id="camera-1",
        session_id="session-1",
    )

    previous_heartbeat = first_session.last_heartbeat_at

    second_session, second_created = await manager.open_session(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert first_created is True
    assert second_created is False
    assert second_session is first_session
    assert second_session.last_heartbeat_at >= previous_heartbeat
    assert await manager.active_session_count() == 1


@pytest.mark.asyncio
async def test_same_session_id_cannot_belong_to_another_camera() -> None:
    manager = SessionManager()

    await manager.open_session(
        camera_id="camera-1",
        session_id="session-1",
    )

    with pytest.raises(
            SessionConflictError,
            match="belongs to another camera",
    ):
        await manager.open_session(
            camera_id="camera-2",
            session_id="session-1",
        )


@pytest.mark.asyncio
async def test_heartbeat_and_frame_update_correct_session() -> None:
    manager = SessionManager()

    session, _ = await manager.open_session(
        camera_id="camera-1",
        session_id="session-1",
    )

    previous_heartbeat = session.last_heartbeat_at

    await manager.heartbeat(
        camera_id="camera-1",
        session_id="session-1",
    )

    await manager.register_frame(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert session.last_heartbeat_at >= previous_heartbeat
    assert session.frame_count == 1


@pytest.mark.asyncio
async def test_missing_session_raises_not_found_error() -> None:
    manager = SessionManager()

    with pytest.raises(
            SessionNotFoundError,
            match="was not found",
    ):
        await manager.get_session("missing-session")


@pytest.mark.asyncio
async def test_close_session_removes_it_and_is_idempotent() -> None:
    manager = SessionManager()

    session, _ = await manager.open_session(
        camera_id="camera-1",
        session_id="session-1",
    )

    first_close_result = await manager.close_session(
        camera_id="camera-1",
        session_id="session-1",
    )

    second_close_result = await manager.close_session(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert first_close_result is session
    assert second_close_result is None
    assert session.status is SessionStatus.CLOSED
    assert session.closed_at is not None
    assert await manager.active_session_count() == 0


@pytest.mark.asyncio
async def test_clear_closes_and_removes_all_sessions() -> None:
    manager = SessionManager()

    first_session, _ = await manager.open_session(
        camera_id="camera-1",
        session_id="session-1",
    )

    second_session, _ = await manager.open_session(
        camera_id="camera-2",
        session_id="session-2",
    )

    await manager.clear()

    assert first_session.status is SessionStatus.CLOSED
    assert second_session.status is SessionStatus.CLOSED
    assert await manager.active_session_count() == 0