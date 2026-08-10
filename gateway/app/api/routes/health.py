from datetime import datetime, timezone
from typing import Literal

from fastapi import APIRouter, Depends
from pydantic import BaseModel

from app.api.dependencies import (
    get_session_frame_ingestion_worker_coordinator,
)
from app.core.config import get_settings
from app.services.session_frame_ingestion_worker import (
    SessionFrameIngestionWorkerCoordinator,
)


router = APIRouter(tags=["Health"])


class HealthResponse(BaseModel):
    status: Literal["UP", "DEGRADED"]
    ai_dispatch_status: Literal["UP", "DEGRADED"]
    ai_dispatch_configured: bool
    ai_dispatch_circuit_open: bool
    service: str
    version: str
    environment: str
    timestamp: datetime


@router.get("/health", response_model=HealthResponse)
async def health_check(
        ingestion_worker_coordinator: (
            SessionFrameIngestionWorkerCoordinator
        ) = Depends(
            get_session_frame_ingestion_worker_coordinator,
        ),
) -> HealthResponse:
    settings = get_settings()
    ingestion_stats = await ingestion_worker_coordinator.stats()
    ai_dispatch_status: Literal["UP", "DEGRADED"]

    if ingestion_stats.ai_dispatch_available:
        ai_dispatch_status = "UP"
    else:
        ai_dispatch_status = "DEGRADED"

    return HealthResponse(
        status=ai_dispatch_status,
        ai_dispatch_status=ai_dispatch_status,
        ai_dispatch_configured=(
            ingestion_stats.ai_dispatch_configured
        ),
        ai_dispatch_circuit_open=(
            ingestion_stats.ai_dispatch_circuit_open
        ),
        service=settings.app_name,
        version=settings.app_version,
        environment=settings.environment,
        timestamp=datetime.now(timezone.utc),
    )