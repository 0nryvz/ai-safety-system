"""
Backend `POST /internal/v1/detections` contract'ı ile birebir eşleşen şemalar.
Alan adları ve kurallar görev planı Adım 4 tablosundan birebir alınmıştır.
"""
from __future__ import annotations

from datetime import datetime

from pydantic import BaseModel, Field, model_validator


class BBox(BaseModel):
    x: float = Field(ge=0.0, le=1.0)
    y: float = Field(ge=0.0, le=1.0)
    width: float = Field(gt=0.0, le=1.0)
    height: float = Field(gt=0.0, le=1.0)

    @model_validator(mode="after")
    def check_within_frame(self) -> "BBox":
        # KRİTİK - backend kuralı: bbox frame dışına taşamaz
        if self.x + self.width > 1.0 + 1e-6:
            raise ValueError("bbox.x + bbox.width 1.0'ı aşamaz")
        if self.y + self.height > 1.0 + 1e-6:
            raise ValueError("bbox.y + bbox.height 1.0'ı aşamaz")
        return self


class DetectionItem(BaseModel):
    label: str
    confidence: float = Field(ge=0.0, le=1.0)
    bbox: BBox


class DetectionRequest(BaseModel):
    """AI Worker'ın backend'e göndereceği payload."""

    event_id: str = Field(alias="eventId")
    camera_id: str = Field(alias="cameraId")
    session_id: str = Field(alias="sessionId")
    frame_timestamp: datetime = Field(alias="frameTimestamp")  # ISO-8601 / UTC, tz zorunlu
    model_version: str = Field(alias="modelVersion")
    inference_ms: float = Field(alias="inferenceMs", ge=0.0)
    # NOT: backend DetectionRequest.detections şu an @NotEmpty (BE1 ile düzeltilecek,
    # bkz. Adım 4 "KRİTİK - boş detection contractı"). Boş liste MVP'de yine de
    # gönderilebilir olacak şekilde burada zorunlu tutmuyoruz; BE1 fix'i
    # gelene kadar backend 400 dönebilir, bu beklenen bir durumdur.
    detections: list[DetectionItem] = Field(default_factory=list)

    @model_validator(mode="after")
    def _require_timezone_aware_timestamp(self) -> "DetectionRequest":
        if self.frame_timestamp.tzinfo is None:
            raise ValueError("frameTimestamp timezone bilgisi içermeli (ISO-8601 / UTC Instant)")
        return self

    # protected_namespaces=(): pydantic v2, "model_" ile başlayan alan adlarında
    # (model_version) uyarı vermesin diye kapatılıyor.
    model_config = {"populate_by_name": True, "protected_namespaces": ()}
