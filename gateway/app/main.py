from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.dependencies import (
    get_session_frame_ingestion_worker_coordinator,
    get_session_frame_queue_manager,
    get_session_frame_ring_buffer_manager,
    get_session_manager,
    get_event_recorder_coordinator,
    get_session_stale_cleanup_coordinator,
)
from app.api.routes.frames import router as frames_router
from app.api.routes.health import router as health_router
from app.api.routes.internal_recordings import (
    router as internal_recordings_router,
)
from app.api.routes.metrics import router as metrics_router
from app.api.routes.sessions import router as sessions_router
from app.core.config import get_settings


settings = get_settings()


@asynccontextmanager
async def lifespan(_: FastAPI) -> AsyncIterator[None]:
    stale_cleanup_coordinator = (
        get_session_stale_cleanup_coordinator()
    )

    await stale_cleanup_coordinator.start()

    try:
        yield
    finally:
        await stale_cleanup_coordinator.stop()

        await (
            get_session_frame_ingestion_worker_coordinator()
            .clear()
        )
        await (
            get_event_recorder_coordinator()
            .clear()
        )
        await get_session_frame_queue_manager().clear()
        await get_session_frame_ring_buffer_manager().clear()
        await get_session_manager().clear()


app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="Camera ingestion, buffering and event recording gateway.",
    lifespan=lifespan,
)

app.include_router(health_router)
app.include_router(metrics_router)
app.include_router(sessions_router)
app.include_router(frames_router)
app.include_router(internal_recordings_router)
