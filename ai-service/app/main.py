import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI

from app.api.routes.health import router as health_router
from app.api.routes.inference import router as inference_router
from app.core.config import get_settings
from app.services.backend_client import BackendDetectionClient
from app.services.class_mapping import load_class_mapping
from app.services.model_runner import ModelRunner

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    settings = get_settings()
    logging.getLogger().setLevel(settings.log_level)

    model_runner = ModelRunner(settings)
    model_runner.load()  # servis açılışında BİR KEZ yüklenir, request başına değil

    backend_client = BackendDetectionClient(settings)
    class_mapping = load_class_mapping(settings.ai_class_mapping_path)

    app.state.settings = settings
    app.state.model_runner = model_runner
    app.state.backend_client = backend_client
    app.state.class_mapping = class_mapping

    logger.info(
        "ai-service başladı | model_loaded=%s | backend_url=%s",
        model_runner.is_loaded,
        settings.backend_detections_url,
    )
    yield
    await backend_client.aclose()
    logger.info("ai-service kapanıyor")


def create_app() -> FastAPI:
    app = FastAPI(title="AI Worker - Inference Service", lifespan=lifespan)
    app.include_router(health_router)
    app.include_router(inference_router)
    return app


app = create_app()
