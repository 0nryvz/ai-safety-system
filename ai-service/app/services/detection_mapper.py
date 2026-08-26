"""
Model çıktısını backend contract'ına çeviren saf fonksiyonlar.
Model bağımsız oldukları için gerçek model gelmeden test yazılabilir.
"""
from __future__ import annotations

from dataclasses import dataclass


class UnsupportedLabelError(ValueError):
    """Backend'in kabul etmediği bir label modelden geldiğinde fırlatılır."""


@dataclass(frozen=True)
class NormalizedBBox:
    x: float
    y: float
    width: float
    height: float
def normalize_and_clamp_bbox(
    x_px: float,
    y_px: float,
    width_px: float,
    height_px: float,
    frame_width: int,
    frame_height: int,
) -> NormalizedBBox:
    if frame_width <= 0 or frame_height <= 0:
        raise ValueError(
            "frame_width ve frame_height pozitif olmalı"
        )

    x1_px = max(
        0.0,
        min(float(x_px), float(frame_width)),
    )
    y1_px = max(
        0.0,
        min(float(y_px), float(frame_height)),
    )

    x2_px = max(
        0.0,
        min(
            float(x_px) + max(float(width_px), 0.0),
            float(frame_width),
        ),
    )
    y2_px = max(
        0.0,
        min(
            float(y_px) + max(float(height_px), 0.0),
            float(frame_height),
        ),
    )

    if x2_px <= x1_px or y2_px <= y1_px:
        raise ValueError(
            "bbox frame içinde pozitif alan üretmiyor"
        )

    # Önce endpoint'leri round ediyoruz.
    # Böylece JSON -> Java BigDecimal tarafında
    # x + width > 1 gibi floating-point taşmaları oluşmuyor.
    x = round(
        x1_px / frame_width,
        6,
    )
    y = round(
        y1_px / frame_height,
        6,
    )
    x2 = round(
        x2_px / frame_width,
        6,
    )
    y2 = round(
        y2_px / frame_height,
        6,
    )

    width = round(
        x2 - x,
        6,
    )
    height = round(
        y2 - y,
        6,
    )

    if width <= 0.0 or height <= 0.0:
        raise ValueError(
            "bbox normalize edildikten sonra pozitif alan üretmiyor"
        )

    return NormalizedBBox(
        x=x,
        y=y,
        width=width,
        height=height,
    )
"""def normalize_and_clamp_bbox(
    x_px: float,
    y_px: float,
    width_px: float,
    height_px: float,
    frame_width: int,
    frame_height: int,
) -> NormalizedBBox:
    
    #Piksel cinsinden bbox'ı (sol-üst köşe + genişlik/yükseklik) 0-1 aralığına
    #normalize eder ve backend kuralına göre frame sınırları içinde clamp eder:
    #x + width <= 1 ve y + height <= 1.
    
    if frame_width <= 0 or frame_height <= 0:
        raise ValueError("frame_width ve frame_height pozitif olmalı")

    x = max(0.0, min(x_px / frame_width, 1.0))
    y = max(0.0, min(y_px / frame_height, 1.0))
    width = max(0.0, min(width_px / frame_width, 1.0))
    height = max(0.0, min(height_px / frame_height, 1.0))

    # x/y clamp edildikten sonra width/height'ın sınırı aşmamasını garanti et
    if x + width > 1.0:
        width = max(0.0, 1.0 - x)
    if y + height > 1.0:
        height = max(0.0, 1.0 - y)

    return NormalizedBBox(x=x, y=y, width=width, height=height)"""


# Backend'in kabul ettiği label listesi (görev planı - vizör YOK)
SUPPORTED_BACKEND_LABELS: frozenset[str] = frozenset(
    {
        "person",
        "gloves",
        "non_gloves",
        "non_welding_jacket",
        "non_welding_mask",
        "welding",
        "welding_apron",
        "welding_jacket",
        "welding_mask",
    }
)


def map_model_label_to_backend_label(
    model_class_name: str, class_mapping: dict[str, str]
) -> str:
    """
    Model class adını backend label'ına çevirir.

    class_mapping: {"model_class_name": "backend_label"} - Adım 0 handoff'ta
    kullanıcıdan alınacak class id/isim listesi ile doldurulacak.
    Desteklenmeyen (örn. vizör) bir label asla eklenmez; eşlemede yoksa
    veya backend listesinde değilse reddedilir.
    """
    backend_label = class_mapping.get(model_class_name)
    if backend_label is None:
        raise UnsupportedLabelError(
            f"Model class '{model_class_name}' için backend mapping tanımlı değil"
        )
    if backend_label not in SUPPORTED_BACKEND_LABELS:
        raise UnsupportedLabelError(
            f"'{backend_label}' backend'in kabul ettiği label listesinde değil"
        )
    return backend_label
