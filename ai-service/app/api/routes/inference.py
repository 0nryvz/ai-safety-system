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
from datetime import datetime

from fastapi import APIRouter, Header, HTTPException, Request, Response
from fastapi.responses import JSONResponse
from pydantic import ValidationError

from app.debug_sample_capture import maybe_capture_debug_sample
from app.schemas.detection import BBox, DetectionItem, DetectionRequest
from app.services.backend_client import BackendClientError, detection_json_body
from app.services.detection_mapper import (
    UnsupportedLabelError,
    map_model_label_to_backend_label,
)
from app.services.model_runner import ModelNotLoadedError

logger = logging.getLogger(__name__)

router = APIRouter()

ALLOWED_CONTENT_TYPES = {"image/jpeg"}


@router.post("/internal/v1/inference/frames", status_code=202)
async def receive_frame(
    request: Request,
    response: Response,
    x_camera_id: str = Header(..., alias="X-Camera-Id"),
    x_session_id: str = Header(..., alias="X-Session-Id"),
    x_frame_timestamp: str = Header(..., alias="X-Frame-Timestamp"),
    x_frame_event_id: str = Header(..., alias="X-Frame-Event-Id"),
):
    content_type = request.headers.get("content-type", "")
    if content_type.split(";")[0].strip() not in ALLOWED_CONTENT_TYPES:
        raise HTTPException(
            status_code=415,
            detail=f"Desteklenmeyen content-type: {content_type or '(boş)'}",
        )

    jpeg_bytes = await request.body()
    if not jpeg_bytes:
        raise HTTPException(status_code=400, detail="Boş JPEG body")

    model_runner = request.app.state.model_runner
    if not model_runner.is_loaded:
        # Model hazır değilse 503 - görev planı hata davranışı
        raise HTTPException(
            status_code=503,
            detail=f"Model hazır değil: {model_runner.load_error}",
        )

    logger.info(
        "Frame alındı camera_id=%s session_id=%s event_id=%s",
        x_camera_id,
        x_session_id,
        x_frame_event_id,
    )

    try:
        result = model_runner.predict(jpeg_bytes)
    except ModelNotLoadedError as exc:
        raise HTTPException(status_code=503, detail=str(exc)) from exc
    except Exception as exc:  # noqa: BLE001 - bozuk kare/inference hatası kontrollü hataya çevrilir
        logger.warning("Bozuk kare/inference hatası event_id=%s: %s", x_frame_event_id, exc)
        raise HTTPException(status_code=400, detail="Kare işlenemedi") from exc

    # Model class isimlerini backend label'larına çevir (vizör vb. desteklenmeyenler reddedilir)
    settings = request.app.state.settings
    class_mapping = request.app.state.class_mapping
    try:
        detection_items = [
            DetectionItem(
                label=map_model_label_to_backend_label(d.label, class_mapping),
                confidence=d.confidence,
                bbox=BBox(x=d.bbox_x, y=d.bbox_y, width=d.bbox_width, height=d.bbox_height),
            )
            for d in result.detections
        ]
    except UnsupportedLabelError as exc:
        logger.error("Desteklenmeyen label event_id=%s: %s", x_frame_event_id, exc)
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    try:
        payload = DetectionRequest(
            eventId=x_frame_event_id,
            cameraId=x_camera_id,
            sessionId=x_session_id,
            frameTimestamp=_parse_frame_timestamp(x_frame_timestamp),
            modelVersion=settings.ai_model_version,
            inferenceMs=round(result.inference_ms),
            detections=detection_items,
        )
    except ValidationError as exc:
        logger.error("Detection payload doğrulanamadı event_id=%s: %s", x_frame_event_id, exc)
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    backend_client = request.app.state.backend_client
    try:
        await backend_client.send(payload)
    except BackendClientError as exc:
        # Backend contract/auth (4xx) ya da bounded retry sonrası kalıcı 5xx.
        # Frame AI Worker tarafından işlendi (Gateway'in tekrar denemesi
        # sorunu çözmez); hata loglanır, Gateway'e yine 202 dönülür.
        logger.error(
            "Backend'e gönderim başarısız event_id=%s status=%s: %s",
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
            detail="Backend detection forwarding failed",
        ) from exc

    # GEÇİCİ DEBUG: trigger varsa 2 sn arayla 5 örnek. send() ile aynı body.
    maybe_capture_debug_sample(jpeg_bytes, detection_json_body(payload))

    return JSONResponse(
        status_code=202,
        content={"eventId": x_frame_event_id, "status": "accepted"},
    )


def _parse_frame_timestamp(raw: str) -> datetime:
    try:
        return datetime.fromisoformat(raw.replace("Z", "+00:00"))
    except ValueError as exc:
        raise HTTPException(
            status_code=400, detail=f"Geçersiz X-Frame-Timestamp: {raw}"
        ) from exc
