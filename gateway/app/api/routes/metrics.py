from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.api.dependencies import (
    get_session_frame_queue_manager,
    get_session_manager,
)
from app.core.config import Settings, get_settings
from app.services.session_frame_queue_manager import (
    SessionFrameQueueManager,
)
from app.services.session_manager import SessionManager


router = APIRouter(tags=["Metrics"])


class GatewayMetricsResponse(BaseModel):
    active_sessions: int
    active_frame_queues: int
    queued_frames: int
    frame_queue_capacity_per_session: int
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
        timestamp=datetime.now(timezone.utc),
    )