from functools import lru_cache
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "camera-ingestion-gateway"
    app_version: str = "0.1.0"
    environment: str = "local"
    local_session_token: str = "dev-session-token"

    frame_queue_max_frames: int = Field(
        default=30,
        ge=1,
    )

    frame_max_bytes: int = Field(
        default=2_097_152,
        ge=1,
    )

    ai_sampling_fps: float = Field(
        default=3.0,
        gt=0,
    )

    ai_dispatch_timeout_seconds: float = Field(
        default=1.0,
        gt=0,
    )

    ai_dispatch_max_retries: int = Field(
        default=1,
        ge=0,
    )

    ai_dispatch_circuit_failure_threshold: int = Field(
        default=3,
        gt=0,
    )

    ai_dispatch_circuit_cooldown_seconds: float = Field(
        default=2.0,
        ge=0,
    )

    ring_buffer_seconds: int = Field(
        default=10,
        ge=5,
        le=10,
    )

    ring_buffer_max_frames: int = Field(
        default=300,
        ge=1,
    )

    ring_buffer_max_bytes: int = Field(
        default=67_108_864,
        ge=1,
    )

    recorder_output_dir: str = "var/recordings"

    recorder_ffmpeg_path: str = "ffmpeg"

    recorder_ffprobe_path: str = "ffprobe"

    model_config = SettingsConfigDict(
        env_prefix="GATEWAY_",
        env_file=".env",
        extra="ignore",
    )



@lru_cache
def get_settings() -> Settings:
    return Settings()
