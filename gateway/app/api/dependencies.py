from functools import lru_cache
from datetime import timedelta
from pathlib import Path

from app.core.config import get_settings
from app.infrastructure.local_session_lifecycle_notifier import (
    LocalCameraSessionLifecycleNotifier,
)
from app.infrastructure.http_session_lifecycle_notifier import (
    HttpCameraSessionLifecycleNotifier,
)
from app.infrastructure.local_session_validator import (
    LocalCameraSessionValidator,
)
from app.services.session_lifecycle_notifier import (
    CameraSessionLifecycleNotifier,
)
from app.services.session_manager import SessionManager
from app.services.session_validator import CameraSessionValidator

from app.services.session_frame_queue_manager import (
    SessionFrameQueueManager,
)
from app.services.session_frame_ingestion_worker import (
    SessionFrameIngestionWorkerCoordinator,
)
from app.services.ai_frame_client import NoOpAIFrameClient
from app.services.session_ai_frame_dispatch_worker import (
    SessionAIFrameDispatchWorkerCoordinator,
)
from app.services.session_ai_frame_sampler import SessionAIFrameSampler
from app.services.session_frame_ring_buffer_manager import (
    SessionFrameRingBufferManager,
)
from app.services.recording_command_coordinator import (
    RecordingCommandCoordinator,
)
from app.infrastructure.ffmpeg_video_encoder import (
    FfmpegVideoEncoder,
)
from app.infrastructure.http_recording_callback_client import (
    HttpRecordingCallbackClient,
)
from app.infrastructure.minio_clip_storage import (
    MinioClipStorage,
)
from app.services.clip_delivery_coordinator import (
    ClipDeliveryCoordinator,
)
from app.services.clip_spool import (
    ClipSpool,
    LocalClipSpool,
)
from app.services.event_recorder import (
    EventRecorderCoordinator,
)
from app.services.clip_storage import (
    ClipStorage,
)
from app.services.recording_callback_client import (
    RecordingCallbackClient,
)
from app.infrastructure.http_ai_frame_client import (
    HttpAIFrameClient,
)

@lru_cache
def get_session_manager() -> SessionManager:
    return SessionManager()


@lru_cache
def get_camera_session_validator() -> CameraSessionValidator:
    settings = get_settings()

    return LocalCameraSessionValidator(
        expected_token=settings.local_session_token,
    )

@lru_cache
def get_camera_session_lifecycle_notifier(
) -> CameraSessionLifecycleNotifier:
    settings = get_settings()

    if not settings.session_lifecycle_http_enabled:
        return LocalCameraSessionLifecycleNotifier()

    return HttpCameraSessionLifecycleNotifier(
        backend_base_url=(
            settings.session_lifecycle_backend_base_url
        ),
        internal_api_key=(
            settings.session_lifecycle_internal_api_key
        ),
    )

@lru_cache
def get_session_frame_queue_manager() -> SessionFrameQueueManager:
    settings = get_settings()

    return SessionFrameQueueManager(
        max_frames=settings.frame_queue_max_frames,
    )


@lru_cache
def get_session_frame_ring_buffer_manager(
) -> SessionFrameRingBufferManager:
    settings = get_settings()

    return SessionFrameRingBufferManager(
        buffer_seconds=settings.ring_buffer_seconds,
        max_frames=settings.ring_buffer_max_frames,
        max_bytes=settings.ring_buffer_max_bytes,
    )


@lru_cache
def get_clip_storage() -> ClipStorage:
    settings = get_settings()

    return MinioClipStorage(
        endpoint=(
            settings.recorder_storage_minio_endpoint
        ),
        access_key=(
            settings.recorder_storage_minio_access_key
        ),
        secret_key=(
            settings.recorder_storage_minio_secret_key
        ),
        bucket=(
            settings.recorder_storage_minio_bucket
        ),
        secure=(
            settings.recorder_storage_minio_secure
        ),
    )


@lru_cache
def get_recording_callback_client(
) -> RecordingCallbackClient:
    settings = get_settings()

    return HttpRecordingCallbackClient(
        backend_base_url=(
            settings.recording_callback_backend_base_url
        ),
        internal_api_key=(
            settings.recording_callback_internal_api_key
        ),
    )


@lru_cache
def get_clip_delivery_coordinator(
) -> ClipDeliveryCoordinator:
    settings = get_settings()

    return ClipDeliveryCoordinator(
        clip_storage=get_clip_storage(),
        clip_spool=get_clip_spool(),
        recording_callback_client=(
            get_recording_callback_client()
        ),
        upload_max_retries=(
            settings.recorder_upload_max_retries
        ),
        upload_initial_backoff_seconds=(
            settings.recorder_upload_initial_backoff_seconds
        ),
        upload_max_backoff_seconds=(
            settings.recorder_upload_max_backoff_seconds
        ),
        callback_max_retries=(
            settings.recording_callback_max_retries
        ),
        callback_initial_backoff_seconds=(
            settings.recording_callback_initial_backoff_seconds
        ),
        callback_max_backoff_seconds=(
            settings.recording_callback_max_backoff_seconds
        ),
    )


@lru_cache
def get_clip_spool() -> ClipSpool:
    settings = get_settings()

    return LocalClipSpool(
        output_dir=Path(settings.recorder_output_dir),
        max_bytes=settings.recorder_spool_max_bytes,
        ttl_seconds=settings.recorder_spool_ttl_seconds,
    )


@lru_cache
def get_event_recorder_coordinator(
) -> EventRecorderCoordinator:
    settings = get_settings()

    video_encoder = FfmpegVideoEncoder(
        output_dir=(
            settings.recorder_output_dir
        ),
        ffmpeg_path=(
            settings.recorder_ffmpeg_path
        ),
        ffprobe_path=(
            settings.recorder_ffprobe_path
        ),
    )

    return EventRecorderCoordinator(
        video_encoder=video_encoder,
        clip_delivery_coordinator=(
            get_clip_delivery_coordinator()
        ),
    )

@lru_cache
def get_session_frame_ingestion_worker_coordinator(
) -> SessionFrameIngestionWorkerCoordinator:
    settings = get_settings()

    ai_frame_sampler = SessionAIFrameSampler(
        sample_interval=timedelta(
            seconds=(1 / settings.ai_sampling_fps),
        )
    )
    if settings.ai_http_enabled:
        ai_frame_client = HttpAIFrameClient(
            ai_base_url=settings.ai_base_url,
            timeout_seconds=(
                settings.ai_dispatch_timeout_seconds
            ),
        )
    else:
        ai_frame_client = NoOpAIFrameClient()

    ai_dispatch_worker_coordinator = (
        SessionAIFrameDispatchWorkerCoordinator(
            ai_frame_client=ai_frame_client,
            ai_configured=settings.ai_http_enabled,
            send_timeout_seconds=(
                settings.ai_dispatch_timeout_seconds
            ),
            max_retries=settings.ai_dispatch_max_retries,
            circuit_failure_threshold=(
                settings.ai_dispatch_circuit_failure_threshold
            ),
            circuit_cooldown_seconds=(
                settings.ai_dispatch_circuit_cooldown_seconds
            ),
        )
    )

    return (
        SessionFrameIngestionWorkerCoordinator(
            ai_frame_sampler=(
                ai_frame_sampler
            ),
            ai_frame_dispatch_worker_coordinator=(
                ai_dispatch_worker_coordinator
            ),
            event_recorder_coordinator=(
                get_event_recorder_coordinator()
            ),
        )
    )


@lru_cache
def get_recording_command_coordinator(
) -> RecordingCommandCoordinator:
    return RecordingCommandCoordinator(
        event_recorder_coordinator=(
            get_event_recorder_coordinator()
        ),
        ring_buffer_manager=(
            get_session_frame_ring_buffer_manager()
        ),
    )
