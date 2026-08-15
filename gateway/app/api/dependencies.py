from functools import lru_cache
from datetime import timedelta

from app.core.config import get_settings
from app.infrastructure.local_session_lifecycle_notifier import (
    LocalCameraSessionLifecycleNotifier,
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
from app.services.event_recorder import (
    EventRecorderCoordinator,
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
    return LocalCameraSessionLifecycleNotifier()

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
    ai_dispatch_worker_coordinator = (
        SessionAIFrameDispatchWorkerCoordinator(
            ai_frame_client=NoOpAIFrameClient(),
            ai_configured=False,
            send_timeout_seconds=settings.ai_dispatch_timeout_seconds,
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
