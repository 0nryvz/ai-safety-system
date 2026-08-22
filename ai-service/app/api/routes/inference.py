"""
Adım 3: Gateway -> AI Worker HTTP sözleşmesi.

POST /internal/v1/inference/frames
Content-Type: image/jpeg
Headers: X-Camera-Id, X-Session-Id, X-Frame-Timestamp, X-Frame-Event-Id
Body: ham JPEG bytes

KRİTİK - eventId / retry idempotency: X-Frame-Event-Id, Gateway (BE4) tarafından
seçilen frame için üretilir ve retry boyunca aynı kalır. AI Worker bu değeri
backend'e eventId olarak aynen taşır; kendi UUID üretmez.
"""

from __future__ import annotations

import logging
import time
from datetime import datetime

from fastapi import (
    APIRouter,
    Header,
    HTTPException,
    Request,
    Response,
)
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.schemas.detection import (
    BBox,
    DetectionItem,
    DetectionRequest,
)
from app.services.backend_client import BackendClientError
from app.services.detection_mapper import (
    UnsupportedLabelError,
    map_model_label_to_backend_label,
)
from app.services.model_runner import (
    InvalidFrameError,
    ModelNotLoadedError,
)

logger = logging.getLogger(__name__)

router = APIRouter()

ALLOWED_CONTENT_TYPES = {"image/jpeg"}


@router.post(
    "/internal/v1/inference/frames",
    status_code=202,
)
async def receive_frame(
    request: Request,
    response: Response,
    x_camera_id: str = Header(
        ...,
        alias="X-Camera-Id",
    ),
    x_session_id: str = Header(
        ...,
        alias="X-Session-Id",
    ),
    x_frame_timestamp: str = Header(
        ...,
        alias="X-Frame-Timestamp",
    ),
    x_frame_event_id: str = Header(
        ...,
        alias="X-Frame-Event-Id",
    ),
):
    content_type = request.headers.get(
        "content-type",
        "",
    )

    if (
        content_type.split(";")[0].strip()
        not in ALLOWED_CONTENT_TYPES
    ):
        raise HTTPException(
            status_code=415,
            detail=(
                "Desteklenmeyen content-type: "
                f"{content_type or '(boş)'}"
            ),
        )

    jpeg_bytes = await request.body()

    if not jpeg_bytes:
        raise HTTPException(
            status_code=400,
            detail="Boş JPEG body",
        )

    model_runner = request.app.state.model_runner
    runtime_metrics = request.app.state.runtime_metrics

    if not model_runner.is_loaded:
        raise HTTPException(
            status_code=503,
            detail=(
                "Model hazır değil: "
                f"{model_runner.load_error}"
            ),
        )

    logger.info(
        "Frame alındı camera_id=%s "
        "session_id=%s event_id=%s",
        x_camera_id,
        x_session_id,
        x_frame_event_id,
    )

    try:
        result = model_runner.predict(jpeg_bytes)

    except ModelNotLoadedError as exc:
        raise HTTPException(
            status_code=503,
            detail=str(exc),
        ) from exc

    except InvalidFrameError as exc:
        runtime_metrics.record_inference_error(
            invalid_jpeg=True
        )

        logger.warning(
            "Bozuk JPEG event_id=%s: %s",
            x_frame_event_id,
            exc,
        )

        raise HTTPException(
            status_code=400,
            detail="Kare işlenemedi",
        ) from exc

    except Exception as exc:
        runtime_metrics.record_inference_error()

        logger.warning(
            "Inference hatası event_id=%s: %s",
            x_frame_event_id,
            exc,
        )

        raise HTTPException(
            status_code=400,
            detail="Kare işlenemedi",
        ) from exc

    runtime_metrics.record_processed()

    settings = request.app.state.settings
    class_mapping = request.app.state.class_mapping

    try:
        detection_items = [
            DetectionItem(
                label=map_model_label_to_backend_label(
                    detection.label,
                    class_mapping,
                ),
                confidence=detection.confidence,
                bbox=BBox(
                    x=detection.bbox_x,
                    y=detection.bbox_y,
                    width=detection.bbox_width,
                    height=detection.bbox_height,
                ),
            )
            for detection in result.detections
        ]

    except UnsupportedLabelError as exc:
        logger.error(
            "Desteklenmeyen label event_id=%s: %s",
            x_frame_event_id,
            exc,
        )

        raise HTTPException(
            status_code=400,
            detail=str(exc),
        ) from exc

    try:
        payload = DetectionRequest(
            eventId=x_frame_event_id,
            cameraId=x_camera_id,
            sessionId=x_session_id,
            frameTimestamp=_parse_frame_timestamp(
                x_frame_timestamp
            ),
            modelVersion=settings.ai_model_version,
            inferenceMs=round(result.inference_ms),
            detections=detection_items,
        )

    except ValidationError as exc:
        logger.error(
            "Detection payload doğrulanamadı "
            "event_id=%s: %s",
            x_frame_event_id,
            exc,
        )

        raise HTTPException(
            status_code=400,
            detail=str(exc),
        ) from exc

    backend_client = request.app.state.backend_client
    dispatch_started = time.perf_counter()

    try:
        await backend_client.send(payload)

    except BackendClientError as exc:
        dispatch_latency_ms = (
            time.perf_counter() - dispatch_started
        ) * 1000

        runtime_metrics.record_backend_dispatch(
            success=False,
            latency_ms=dispatch_latency_ms,
        )

        logger.error(
            "Backend'e gönderim başarısız "
            "event_id=%s status=%s: %s",
            x_frame_event_id,
            exc.status_code,
            exc,
        )

        if (
            exc.status_code is not None
            and 400 <= exc.status_code < 500
        ):
            raise HTTPException(
                status_code=exc.status_code,
                detail=(
                    "Backend detection request rejected "
                    f"with status={exc.status_code}"
                ),
            ) from exc

        raise HTTPException(
            status_code=502,
            detail=(
                "Backend detection forwarding failed"
            ),
        ) from exc

    dispatch_latency_ms = (
        time.perf_counter() - dispatch_started
    ) * 1000

    runtime_metrics.record_backend_dispatch(
        success=True,
        latency_ms=dispatch_latency_ms,
    )

    return JSONResponse(
        status_code=202,
        content={
            "eventId": x_frame_event_id,
            "status": "accepted",
        },
    )


def _parse_frame_timestamp(raw: str) -> datetime:
    try:
        return datetime.fromisoformat(
            raw.replace("Z", "+00:00")
        )

    except ValueError as exc:
        raise HTTPException(
            status_code=400,
            detail=(
                "Geçersiz X-Frame-Timestamp: "
                f"{raw}"
            ),
        ) from exc