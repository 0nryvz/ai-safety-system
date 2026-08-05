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

    model_config = SettingsConfigDict(
        env_prefix="GATEWAY_",
        env_file=".env",
        extra="ignore",
    )



@lru_cache
def get_settings() -> Settings:
    return Settings()
