import pytest
from pydantic import ValidationError

from app.core.config import Settings


RING_BUFFER_ENV_VARS = (
    "GATEWAY_RING_BUFFER_SECONDS",
    "GATEWAY_RING_BUFFER_MAX_FRAMES",
    "GATEWAY_RING_BUFFER_MAX_BYTES",
)


@pytest.fixture(autouse=True)
def clear_ring_buffer_env(monkeypatch) -> None:
    for env_var in RING_BUFFER_ENV_VARS:
        monkeypatch.delenv(env_var, raising=False)


def test_ring_buffer_settings_defaults() -> None:
    settings = Settings(_env_file=None)

    assert settings.ring_buffer_seconds == 10
    assert settings.ring_buffer_max_frames == 300
    assert settings.ring_buffer_max_bytes == 67_108_864


def test_ring_buffer_settings_environment_override(monkeypatch) -> None:
    monkeypatch.setenv("GATEWAY_RING_BUFFER_SECONDS", "5")
    monkeypatch.setenv("GATEWAY_RING_BUFFER_MAX_FRAMES", "42")
    monkeypatch.setenv("GATEWAY_RING_BUFFER_MAX_BYTES", "1024")

    settings = Settings(_env_file=None)

    assert settings.ring_buffer_seconds == 5
    assert settings.ring_buffer_max_frames == 42
    assert settings.ring_buffer_max_bytes == 1024


def test_ring_buffer_seconds_accepts_minimum() -> None:
    settings = Settings(
        ring_buffer_seconds=5,
        _env_file=None,
    )

    assert settings.ring_buffer_seconds == 5


def test_ring_buffer_seconds_accepts_maximum() -> None:
    settings = Settings(
        ring_buffer_seconds=10,
        _env_file=None,
    )

    assert settings.ring_buffer_seconds == 10


def test_ring_buffer_seconds_rejects_below_minimum() -> None:
    with pytest.raises(ValidationError):
        Settings(
            ring_buffer_seconds=4,
            _env_file=None,
        )


def test_ring_buffer_seconds_rejects_above_maximum() -> None:
    with pytest.raises(ValidationError):
        Settings(
            ring_buffer_seconds=11,
            _env_file=None,
        )


def test_ring_buffer_max_frames_rejects_zero() -> None:
    with pytest.raises(ValidationError):
        Settings(
            ring_buffer_max_frames=0,
            _env_file=None,
        )


def test_ring_buffer_max_bytes_rejects_zero() -> None:
    with pytest.raises(ValidationError):
        Settings(
            ring_buffer_max_bytes=0,
            _env_file=None,
        )
