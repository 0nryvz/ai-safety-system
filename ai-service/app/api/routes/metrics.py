"""AI Worker runtime metrics endpoint'i."""

from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/internal/v1/metrics")
def get_runtime_metrics(request: Request) -> dict:
    return request.app.state.runtime_metrics.snapshot()