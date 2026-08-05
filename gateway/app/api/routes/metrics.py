from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.api.dependencies import (
    get_session_frame_ingestion_worker_coordinator,
    get_session_frame_queue_manager,
    get_session_frame_ring_buffer_manager,
    get_session_manager,
)
from app.core.config import Settings, get_settings
from app.services.session_frame_ingestion_worker import (
    SessionFrameIngestionWorkerCoordinator,
)
from app.services.session_frame_queue_manager import (
    SessionFrameQueueManager,
)
from app.services.session_frame_ring_buffer_manager import (
    SessionFrameRingBufferManager,
)
from app.services.session_manager import SessionManager


router = APIRouter(tags=["Metrics"])


class GatewayMetricsResponse(BaseModel):
    active_sessions: int
    active_frame_queues: int
    queued_frames: int
    frame_queue_capacity_per_session: int
    active_ring_buffers: int
    buffered_frames: int
    buffered_bytes: int
    ring_buffer_evicted_frames: int
    ring_buffer_evicted_bytes: int
    ring_buffer_seconds: int
    ring_buffer_max_frames_per_session: int
    ring_buffer_max_bytes_per_session: int
    active_ingestion_workers: int
    timestamp: datetime


@router.get(
    "/metrics",
    response_model=GatewayMetricsResponse,
)
async def gateway_metrics(
        session_manager: SessionManager = Depends(
            get_session_manager,
        ),
        session_frame_queue_manager: SessionFrameQueueManager = Depends(
            get_session_frame_queue_manager,
        ),
        session_frame_ring_buffer_manager: (
            SessionFrameRingBufferManager
        ) = Depends(
            get_session_frame_ring_buffer_manager,
        ),
        ingestion_worker_coordinator: (
            SessionFrameIngestionWorkerCoordinator
        ) = Depends(
            get_session_frame_ingestion_worker_coordinator,
        ),
        settings: Settings = Depends(get_settings),
) -> GatewayMetricsResponse:
    return GatewayMetricsResponse(
        active_sessions=(
            await session_manager.active_session_count()
        ),
        active_frame_queues=(
            await session_frame_queue_manager.active_queue_count()
        ),
        queued_frames=(
            await session_frame_queue_manager
            .total_queued_frame_count()
        ),
        frame_queue_capacity_per_session=(
            settings.frame_queue_max_frames
        ),
        active_ring_buffers=(
            await session_frame_ring_buffer_manager
            .active_buffer_count()
        ),
        buffered_frames=(
            await session_frame_ring_buffer_manager
            .total_buffered_frame_count()
        ),
        buffered_bytes=(
            await session_frame_ring_buffer_manager
            .total_buffered_bytes()
        ),
        ring_buffer_evicted_frames=(
            await session_frame_ring_buffer_manager
            .total_evicted_frame_count()
        ),
        ring_buffer_evicted_bytes=(
            await session_frame_ring_buffer_manager
            .total_evicted_bytes()
        ),
        ring_buffer_seconds=settings.ring_buffer_seconds,
        ring_buffer_max_frames_per_session=(
            settings.ring_buffer_max_frames
        ),
        ring_buffer_max_bytes_per_session=(
            settings.ring_buffer_max_bytes
        ),
        active_ingestion_workers=(
            await ingestion_worker_coordinator.active_worker_count()
        ),
        timestamp=datetime.now(timezone.utc),
    )
