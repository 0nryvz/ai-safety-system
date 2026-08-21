"""
ModelRunner: model yükleme ve inference çalıştırma sorumluluğu tek burada.

Adım 0 handoff sonucu:
    model: yolo26s.pt (Ultralytics YOLO), imgsz=640, 6 class
    names: ['Person', 'gloves', 'welding', 'welding_apron', 'welding_jacket', 'welding_mask']
"""
from __future__ import annotations

import logging
import time
from dataclasses import dataclass, field

import cv2
import numpy as np

from app.core.config import Settings
from app.services.detection_mapper import normalize_and_clamp_bbox

logger = logging.getLogger(__name__)


@dataclass
class Detection:
    label: str  # HAM model class ismi - backend label'ına inference.py'de çevrilir
    confidence: float
    bbox_x: float
    bbox_y: float
    bbox_width: float
    bbox_height: float


@dataclass
class InferenceResult:
    detections: list[Detection] = field(default_factory=list)
    inference_ms: float = 0.0


class ModelNotLoadedError(RuntimeError):
    pass


class InvalidFrameError(ValueError):
    """Bozuk/decode edilemeyen JPEG kare için fırlatılır."""


class ModelRunner:
    """Model artifact'i açılışta bir kez yükler; her request'te reload etmez."""

    def __init__(self, settings: Settings):
        self._settings = settings
        self._model = None
        self._loaded = False
        self._load_error: str | None = None

    @property
    def is_loaded(self) -> bool:
        return self._loaded

    @property
    def load_error(self) -> str | None:
        return self._load_error

    def load(self) -> None:
        model_path = self._settings.ai_model_path
        if not model_path:
            self._loaded = False
            self._load_error = "AI_MODEL_PATH tanımlı değil - model artifact teslim edilmedi (Adım 0)."
            logger.warning(self._load_error)
            return

        try:
            from ultralytics import YOLO  # lazy import - opsiyonel ağır bağımlılık

            self._model = YOLO(model_path)
            self._loaded = True
            self._load_error = None
            logger.info(
                "Model yüklendi path=%s device=%s classes=%s",
                model_path,
                self._settings.ai_model_device,
                self._model.names,
            )
        except Exception as exc:  # noqa: BLE001 - health endpoint'inde raporlanır
            self._loaded = False
            self._load_error = str(exc)
            logger.exception("Model yüklenemedi")

    def predict(self, jpeg_bytes: bytes) -> InferenceResult:
        if not self._loaded or self._model is None:
            raise ModelNotLoadedError("Model yüklenmedi; inference çalıştırılamaz.")

        np_arr = np.frombuffer(jpeg_bytes, dtype=np.uint8)
        image = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)

        if image is None:
            raise InvalidFrameError("JPEG decode edilemedi - bozuk kare.")

        frame_height, frame_width = image.shape[:2]

        # YOLO'nun sonuçları daha class bazlı filtreleme yapmadan önce
        # silmemesi için en düşük özel threshold ile inference çalıştırılır.
        model_confidence_floor = min(
            self._settings.confidence_threshold,
            self._settings.welding_confidence_threshold,

            self._settings.gloves_confidence_threshold,

            self._settings.welding_mask_confidence_threshold,

            self._settings.non_gloves_confidence_threshold,
        )

        start = time.perf_counter()

        results = self._model.predict(
            source=image,
            conf=model_confidence_floor,
            iou=self._settings.iou_threshold,
            imgsz=640,
            device=self._settings.ai_model_device,
            verbose=False,
        )

        inference_ms = (time.perf_counter() - start) * 1000

        detections: list[Detection] = []

        if results:
            result = results[0]
            names = result.names or self._model.names

            for box in result.boxes:
                cls_id = int(box.cls[0])
                confidence = float(box.conf[0])
                label = str(names[cls_id])

                normalized_label = label.lower()

                if normalized_label == "welding":
                    threshold = (
                        self._settings.welding_confidence_threshold
                    )
                elif normalized_label == "welding_mask":
                   threshold = (
                       self._settings.welding_mask_confidence_threshold
                   )
                elif normalized_label == "non_gloves":
                    threshold = (
                        self._settings.non_gloves_confidence_threshold
                    )

                elif normalized_label == "gloves":
                    threshold = (
                        self._settings.gloves_confidence_threshold
                    )

                else:
                    threshold = (
                        self._settings.confidence_threshold
                    )

                # Class-specific threshold
                if confidence < threshold:
                    continue

                x1, y1, x2, y2 = (
                    float(v)
                    for v in box.xyxy[0]
                )

                try:
                    norm = normalize_and_clamp_bbox(
                        x_px=x1,
                        y_px=y1,
                        width_px=x2 - x1,
                        height_px=y2 - y1,
                        frame_width=frame_width,
                        frame_height=frame_height,
                    )

                except ValueError as exc:
                    logger.warning(
                        "Geçersiz bbox atlandı "
                        "class=%s bbox=%s error=%s",
                        label,
                        [x1, y1, x2, y2],
                        exc,
                    )
                    continue

                detections.append(
                    Detection(
                        label=label,
                        confidence=confidence,
                        bbox_x=norm.x,
                        bbox_y=norm.y,
                        bbox_width=norm.width,
                        bbox_height=norm.height,
                    )
                )

        return InferenceResult(
            detections=detections,
            inference_ms=inference_ms,
        )

