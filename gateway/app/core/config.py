from functools import lru_cache
from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "camera-ingestion-gateway"
    app_version: str = "0.1.0"
    environment: str = "local"
    local_session_token: str = "dev-session-token"

    backend_base_url: str = Field(
        default="",
        validation_alias="BACKEND_BASE_URL",
    )

    internal_api_key: str = Field(
        default="",
        validation_alias="INTERNAL_API_KEY",
    )

    ai_worker_base_url: str = Field(
        default="",
        validation_alias="AI_WORKER_BASE_URL",
    )

    minio_endpoint: str = Field(
        default="",
        validation_alias="MINIO_ENDPOINT",
    )

    minio_access_key: str = Field(
        default="",
        validation_alias="MINIO_ACCESS_KEY",
    )

    minio_secret_key: str = Field(
        default="",
        validation_alias="MINIO_SECRET_KEY",
    )

    minio_bucket: str = Field(
        default="",
        validation_alias="MINIO_BUCKET",
    )

    session_lifecycle_http_enabled: bool = False

    session_lifecycle_backend_base_url: str = (
        "http://localhost:8080"
    )

    session_lifecycle_internal_api_key: str = ""

    session_stale_timeout_seconds: float = Field(
        default=30.0,
        gt=0,
    )

    session_cleanup_interval_seconds: float = Field(
        default=5.0,
        gt=0,
    )

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

    ai_http_enabled: bool = False

    ai_base_url: str = "http://localhost:8001"

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

    recorder_spool_max_bytes: int = Field(
        default=536_870_912,
        ge=1,
    )

    recorder_spool_ttl_seconds: int = Field(
        default=86_400,
        ge=0,
    )

    recorder_ffmpeg_path: str = "ffmpeg"

    recorder_ffprobe_path: str = "ffprobe"

    recorder_storage_minio_endpoint: str = "localhost:9000"

    recorder_storage_minio_access_key: str = ""

    recorder_storage_minio_secret_key: str = ""

    recorder_storage_minio_bucket: str = ""

    recorder_storage_minio_secure: bool = False

    recorder_upload_max_retries: int = Field(
        default=2,
        ge=0,
    )

    recorder_upload_initial_backoff_seconds: float = Field(
        default=0.25,
        ge=0,
    )

    recorder_upload_max_backoff_seconds: float = Field(
        default=2.0,
        ge=0,
    )

    recording_callback_backend_base_url: str = "http://localhost:8080"

    recording_callback_internal_api_key: str = ""

    recording_callback_max_retries: int = Field(
        default=3,
        ge=0,
    )

    recording_callback_initial_backoff_seconds: float = Field(
        default=0.5,
        ge=0,
    )

    recording_callback_max_backoff_seconds: float = Field(
        default=5.0,
        ge=0,
    )

    @property
    def effective_session_lifecycle_backend_base_url(
            self,
    ) -> str:
        return (
                self.backend_base_url
                or self.session_lifecycle_backend_base_url
        )


    @property
    def effective_recording_callback_backend_base_url(
            self,
    ) -> str:
        return (
                self.backend_base_url
                or self.recording_callback_backend_base_url
        )


    @property
    def effective_session_lifecycle_internal_api_key(
            self,
    ) -> str:
        return (
                self.internal_api_key
                or self.session_lifecycle_internal_api_key
        )


    @property
    def effective_recording_callback_internal_api_key(
            self,
    ) -> str:
        return (
                self.internal_api_key
                or self.recording_callback_internal_api_key
        )


    @property
    def effective_ai_base_url(
            self,
    ) -> str:
        return (
                self.ai_worker_base_url
                or self.ai_base_url
        )


    @property
    def effective_recorder_storage_minio_endpoint(
            self,
    ) -> str:
        endpoint = (
                self.minio_endpoint
                or self.recorder_storage_minio_endpoint
        ).strip()

        if endpoint.startswith("http://"):
            return (
                endpoint
                .removeprefix("http://")
                .rstrip("/")
            )

        if endpoint.startswith("https://"):
            return (
                endpoint
                .removeprefix("https://")
                .rstrip("/")
            )

        return endpoint.rstrip("/")


    @property
    def effective_recorder_storage_minio_access_key(
            self,
    ) -> str:
        return (
                self.minio_access_key
                or self.recorder_storage_minio_access_key
        )


    @property
    def effective_recorder_storage_minio_secret_key(
            self,
    ) -> str:
        return (
                self.minio_secret_key
                or self.recorder_storage_minio_secret_key
        )


    @property
    def effective_recorder_storage_minio_bucket(
            self,
    ) -> str:
        return (
                self.minio_bucket
                or self.recorder_storage_minio_bucket
        )


    @property
    def effective_recorder_storage_minio_secure(
            self,
    ) -> bool:
        endpoint = (
            self.minio_endpoint
            .strip()
            .lower()
        )

        if endpoint.startswith("https://"):
            return True

        if endpoint.startswith("http://"):
            return False

        return self.recorder_storage_minio_secure

    model_config = SettingsConfigDict(
        env_prefix="GATEWAY_",
        env_file=".env",
        extra="ignore",
        populate_by_name=True,
    )





@lru_cache
def get_settings() -> Settings:
    return Settings()
