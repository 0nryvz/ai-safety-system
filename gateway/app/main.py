from fastapi import FastAPI

from app.api.routes.frames import router as frames_router
from app.api.routes.health import router as health_router
from app.api.routes.metrics import router as metrics_router
from app.api.routes.sessions import router as sessions_router
from app.core.config import get_settings


settings = get_settings()

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="Camera ingestion, buffering and event recording gateway.",
)

app.include_router(health_router)
app.include_router(metrics_router)
app.include_router(sessions_router)
app.include_router(frames_router)