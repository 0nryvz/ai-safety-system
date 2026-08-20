from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/health")
def health(request: Request):
    model_runner = request.app.state.model_runner
    settings = request.app.state.settings

    return {
        "status": "ok",
        "service": settings.service_name,
        "model": {
            "loaded": model_runner.is_loaded,
            "version": settings.ai_model_version,
            "device": settings.ai_model_device,
            "error": model_runner.load_error,
        },
    }
