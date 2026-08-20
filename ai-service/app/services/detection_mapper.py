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
    """
    Piksel cinsinden bbox'ı (sol-üst köşe + genişlik/yükseklik) 0-1 aralığına
    normalize eder ve backend kuralına göre frame sınırları içinde clamp eder:
    x + width <= 1 ve y + height <= 1.
    """
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

    return NormalizedBBox(x=x, y=y, width=width, height=height)


# Backend'in kabul ettiği label listesi (görev planı - vizör YOK)
SUPPORTED_BACKEND_LABELS: frozenset[str] = frozenset(
    {
        "person",
        "welding",
        "welding_mask",
        "welding_apron",
        "gloves",
        "welding_jacket",
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
