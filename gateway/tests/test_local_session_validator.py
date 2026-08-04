import pytest

from app.infrastructure.local_session_validator import (
    LocalCameraSessionValidator,
)


@pytest.mark.asyncio
async def test_valid_token_accepts_active_camera_session() -> None:
    validator = LocalCameraSessionValidator(
        expected_token="dev-session-token",
    )

    result = await validator.validate_open_session(
        camera_id="camera-1",
        session_id="session-1",
        session_token="dev-session-token",
    )

    assert result.is_valid is True
    assert result.camera_active is True
    assert result.reason is None


@pytest.mark.asyncio
async def test_invalid_token_rejects_session() -> None:
    validator = LocalCameraSessionValidator(
        expected_token="dev-session-token",
    )

    result = await validator.validate_open_session(
        camera_id="camera-1",
        session_id="session-1",
        session_token="wrong-token",
    )

    assert result.is_valid is False
    assert result.camera_active is False
    assert result.reason == "INVALID_SESSION_TOKEN"


@pytest.mark.asyncio
@pytest.mark.parametrize(
    ("camera_id", "session_id"),
    [
        ("", "session-1"),
        ("camera-1", ""),
    ],
)
async def test_missing_session_identifier_is_rejected(
        camera_id: str,
        session_id: str,
) -> None:
    validator = LocalCameraSessionValidator(
        expected_token="dev-session-token",
    )

    result = await validator.validate_open_session(
        camera_id=camera_id,
        session_id=session_id,
        session_token="dev-session-token",
    )

    assert result.is_valid is False
    assert result.camera_active is False
    assert result.reason == "INVALID_SESSION_IDENTIFIERS"