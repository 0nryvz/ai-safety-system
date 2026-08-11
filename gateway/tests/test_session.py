import pytest

from app.domain.session import CameraSessionContext, SessionStatus


def test_new_session_starts_active() -> None:
    session = CameraSessionContext(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert session.status is SessionStatus.ACTIVE
    assert session.closed_at is None
    assert session.frame_count == 0
    assert session.dropped_frame_count == 0
    assert session.opened_at.tzinfo is not None


def test_session_tracks_frames_and_heartbeat() -> None:
    session = CameraSessionContext(
        camera_id="camera-1",
        session_id="session-1",
    )

    previous_heartbeat = session.last_heartbeat_at

    session.register_frame()
    session.register_dropped_frame()
    session.heartbeat()

    assert session.frame_count == 1
    assert session.dropped_frame_count == 1
    assert session.last_heartbeat_at >= previous_heartbeat


def test_close_is_idempotent_and_blocks_new_activity() -> None:
    session = CameraSessionContext(
        camera_id="camera-1",
        session_id="session-1",
    )

    session.close()
    first_closed_at = session.closed_at

    session.close()

    assert session.status is SessionStatus.CLOSED
    assert session.closed_at == first_closed_at

    with pytest.raises(
            ValueError,
            match="Closed session cannot receive frames",
    ):
        session.register_frame()

    with pytest.raises(
            ValueError,
            match="Closed session cannot receive heartbeat",
    ):
        session.heartbeat()