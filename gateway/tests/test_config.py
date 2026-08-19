import pytest
from pydantic import ValidationError

from app.core.config import Settings


RING_BUFFER_ENV_VARS = (
    "GATEWAY_RING_BUFFER_SECONDS",
    "GATEWAY_RING_BUFFER_MAX_FRAMES",
    "GATEWAY_RING_BUFFER_MAX_BYTES",
)

AI_DISPATCH_ENV_VARS = (
    "GATEWAY_AI_SAMPLING_FPS",
    "GATEWAY_AI_DISPATCH_TIMEOUT_SECONDS",
    "GATEWAY_AI_DISPATCH_MAX_RETRIES",
    "GATEWAY_AI_DISPATCH_CIRCUIT_FAILURE_THRESHOLD",
    "GATEWAY_AI_DISPATCH_CIRCUIT_COOLDOWN_SECONDS",
)

MINIO_ENV_VARS = (
    "GATEWAY_RECORDER_SPOOL_MAX_BYTES",
    "GATEWAY_RECORDER_SPOOL_TTL_SECONDS",
    "GATEWAY_RECORDER_STORAGE_MINIO_ENDPOINT",
    "GATEWAY_RECORDER_STORAGE_MINIO_ACCESS_KEY",
    "GATEWAY_RECORDER_STORAGE_MINIO_SECRET_KEY",
    "GATEWAY_RECORDER_STORAGE_MINIO_BUCKET",
    "GATEWAY_RECORDER_STORAGE_MINIO_SECURE",
    "GATEWAY_RECORDER_UPLOAD_MAX_RETRIES",
    "GATEWAY_RECORDER_UPLOAD_INITIAL_BACKOFF_SECONDS",
    "GATEWAY_RECORDER_UPLOAD_MAX_BACKOFF_SECONDS",
)

RECORDING_CALLBACK_ENV_VARS = (
    "GATEWAY_RECORDING_CALLBACK_BACKEND_BASE_URL",
    "GATEWAY_RECORDING_CALLBACK_INTERNAL_API_KEY",
    "GATEWAY_RECORDING_CALLBACK_MAX_RETRIES",
    "GATEWAY_RECORDING_CALLBACK_INITIAL_BACKOFF_SECONDS",
    "GATEWAY_RECORDING_CALLBACK_MAX_BACKOFF_SECONDS",
)

SESSION_LIFECYCLE_ENV_VARS = (
    "GATEWAY_SESSION_LIFECYCLE_HTTP_ENABLED",
    "GATEWAY_SESSION_LIFECYCLE_BACKEND_BASE_URL",
    "GATEWAY_SESSION_LIFECYCLE_INTERNAL_API_KEY",
)


@pytest.fixture(autouse=True)
def clear_ring_buffer_env(monkeypatch) -> None:
    for env_var in (
            RING_BUFFER_ENV_VARS
            + AI_DISPATCH_ENV_VARS
            + MINIO_ENV_VARS
            + RECORDING_CALLBACK_ENV_VARS
            + SESSION_LIFECYCLE_ENV_VARS
    ):
        monkeypatch.delenv(env_var, raising=False)


def test_ring_buffer_settings_defaults() -> None:
    settings = Settings(_env_file=None)

    assert settings.ring_buffer_seconds == 10
    assert settings.ring_buffer_max_frames == 300
    assert settings.ring_buffer_max_bytes == 67_108_864
    assert settings.ai_sampling_fps == 3.0
    assert settings.ai_dispatch_timeout_seconds == 1.0
    assert settings.ai_dispatch_max_retries == 1
    assert settings.ai_dispatch_circuit_failure_threshold == 3
    assert settings.ai_dispatch_circuit_cooldown_seconds == 2.0
    assert settings.recorder_storage_minio_endpoint == "localhost:9000"
    assert settings.recorder_storage_minio_access_key == ""
    assert settings.recorder_storage_minio_secret_key == ""
    assert settings.recorder_storage_minio_bucket == ""
    assert settings.recorder_storage_minio_secure is False
    assert settings.recorder_upload_max_retries == 2
    assert settings.recorder_upload_initial_backoff_seconds == 0.25
    assert settings.recorder_upload_max_backoff_seconds == 2.0
    assert settings.recorder_spool_max_bytes == 536_870_912
    assert settings.recorder_spool_ttl_seconds == 86_400
    assert settings.recording_callback_backend_base_url == "http://localhost:8080"
    assert settings.recording_callback_internal_api_key == ""
    assert settings.recording_callback_max_retries == 3
    assert settings.recording_callback_initial_backoff_seconds == 0.5
    assert settings.recording_callback_max_backoff_seconds == 5.0
    assert settings.session_lifecycle_http_enabled is False
    assert (
            settings.session_lifecycle_backend_base_url
            == "http://localhost:8080"
    )
    assert settings.session_lifecycle_internal_api_key == ""


def test_recording_callback_settings_environment_override(
        monkeypatch,
) -> None:
    monkeypatch.setenv(
        "GATEWAY_RECORDING_CALLBACK_BACKEND_BASE_URL",
        "http://backend.internal:8080",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDING_CALLBACK_INTERNAL_API_KEY",
        "gateway-internal-key",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDING_CALLBACK_MAX_RETRIES",
        "4",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDING_CALLBACK_INITIAL_BACKOFF_SECONDS",
        "0.2",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDING_CALLBACK_MAX_BACKOFF_SECONDS",
        "1.5",
    )

    settings = Settings(_env_file=None)

    assert (
        settings.recording_callback_backend_base_url
        == "http://backend.internal:8080"
    )
    assert settings.recording_callback_internal_api_key == "gateway-internal-key"
    assert settings.recording_callback_max_retries == 4
    assert settings.recording_callback_initial_backoff_seconds == 0.2
    assert settings.recording_callback_max_backoff_seconds == 1.5


def test_recorder_spool_settings_environment_override(
        monkeypatch,
) -> None:
    monkeypatch.setenv(
        "GATEWAY_RECORDER_SPOOL_MAX_BYTES",
        "1048576",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDER_SPOOL_TTL_SECONDS",
        "90",
    )

    settings = Settings(_env_file=None)

    assert settings.recorder_spool_max_bytes == 1_048_576
    assert settings.recorder_spool_ttl_seconds == 90


def test_minio_settings_environment_override(monkeypatch) -> None:
    monkeypatch.setenv(
        "GATEWAY_RECORDER_STORAGE_MINIO_ENDPOINT",
        "minio.internal:9000",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDER_STORAGE_MINIO_ACCESS_KEY",
        "gateway-access",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDER_STORAGE_MINIO_SECRET_KEY",
        "gateway-secret",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDER_STORAGE_MINIO_BUCKET",
        "private-recordings",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDER_STORAGE_MINIO_SECURE",
        "true",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDER_UPLOAD_MAX_RETRIES",
        "6",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDER_UPLOAD_INITIAL_BACKOFF_SECONDS",
        "0.4",
    )
    monkeypatch.setenv(
        "GATEWAY_RECORDER_UPLOAD_MAX_BACKOFF_SECONDS",
        "3.0",
    )

    settings = Settings(_env_file=None)

    assert settings.recorder_storage_minio_endpoint == "minio.internal:9000"
    assert settings.recorder_storage_minio_access_key == "gateway-access"
    assert settings.recorder_storage_minio_secret_key == "gateway-secret"
    assert settings.recorder_storage_minio_bucket == "private-recordings"
    assert settings.recorder_storage_minio_secure is True
    assert settings.recorder_upload_max_retries == 6
    assert settings.recorder_upload_initial_backoff_seconds == 0.4
    assert settings.recorder_upload_max_backoff_seconds == 3.0


def test_ai_dispatch_settings_environment_override(monkeypatch) -> None:
    monkeypatch.setenv("GATEWAY_AI_SAMPLING_FPS", "2.5")
    monkeypatch.setenv("GATEWAY_AI_DISPATCH_TIMEOUT_SECONDS", "0.2")
    monkeypatch.setenv("GATEWAY_AI_DISPATCH_MAX_RETRIES", "3")
    monkeypatch.setenv("GATEWAY_AI_DISPATCH_CIRCUIT_FAILURE_THRESHOLD", "7")
    monkeypatch.setenv("GATEWAY_AI_DISPATCH_CIRCUIT_COOLDOWN_SECONDS", "4.5")

    settings = Settings(_env_file=None)

    assert settings.ai_sampling_fps == 2.5
    assert settings.ai_dispatch_timeout_seconds == 0.2
    assert settings.ai_dispatch_max_retries == 3
    assert settings.ai_dispatch_circuit_failure_threshold == 7
    assert settings.ai_dispatch_circuit_cooldown_seconds == 4.5


def test_ai_sampling_fps_rejects_zero() -> None:
    with pytest.raises(ValidationError):
        Settings(
            ai_sampling_fps=0,
            _env_file=None,
        )


def test_ai_dispatch_timeout_rejects_zero() -> None:
    with pytest.raises(ValidationError):
        Settings(
            ai_dispatch_timeout_seconds=0,
            _env_file=None,
        )


def test_ai_dispatch_max_retries_rejects_negative() -> None:
    with pytest.raises(ValidationError):
        Settings(
            ai_dispatch_max_retries=-1,
            _env_file=None,
        )


def test_ai_dispatch_circuit_failure_threshold_rejects_zero() -> None:
    with pytest.raises(ValidationError):
        Settings(
            ai_dispatch_circuit_failure_threshold=0,
            _env_file=None,
        )


def test_ai_dispatch_circuit_cooldown_rejects_negative() -> None:
    with pytest.raises(ValidationError):
        Settings(
            ai_dispatch_circuit_cooldown_seconds=-0.1,
            _env_file=None,
        )


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

def test_session_lifecycle_settings_environment_override(
        monkeypatch,
) -> None:
    monkeypatch.setenv(
        "GATEWAY_SESSION_LIFECYCLE_HTTP_ENABLED",
        "true",
    )
    monkeypatch.setenv(
        "GATEWAY_SESSION_LIFECYCLE_BACKEND_BASE_URL",
        "http://backend.internal:8080",
    )
    monkeypatch.setenv(
        "GATEWAY_SESSION_LIFECYCLE_INTERNAL_API_KEY",
        "test-internal-key",
    )

    settings = Settings(_env_file=None)

    assert settings.session_lifecycle_http_enabled is True
    assert (
            settings.session_lifecycle_backend_base_url
            == "http://backend.internal:8080"
    )
    assert (
            settings.session_lifecycle_internal_api_key
            == "test-internal-key"
    )
