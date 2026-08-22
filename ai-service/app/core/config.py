"""
Uygulama konfigürasyonu.

Görev planı Adım 1 kabul kriteri: model path / backend URL hard-code EDİLMEZ,
her şey environment değişkeninden okunur. Yerel geliştirme için ai-service/.env
dosyası kullanılabilir (bkz. .env.example).
"""
from functools import lru_cache
from typing import Optional
from pathlib import Path

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict

ROOT_ENV_FILE = Path(__file__).resolve().parents[3] / ".env"



class Settings(BaseSettings):
    model_config = SettingsConfigDict(
    # Tek config kaynağı repo rootundaki .env dosyasıdır.
    # ai-service/.env bilinçli olarak okunmaz.
        env_file=ROOT_ENV_FILE,
        env_file_encoding="utf-8",
        extra="ignore",
    )

    # --- Servis ---
    service_name: str = "ai-worker"
    log_level: str = "INFO"

    # --- Model (Adım 0 / Adım 2) ---
    # AI_MODEL_PATH: teslim alınan model artifact dosyasının yolu (.pt / .onnx vb.)
    ai_model_path: Optional[str] = Field(default=None, alias="AI_MODEL_PATH")
    # AI_MODEL_VERSION: modelVersion olarak backend'e gönderilecek sürüm etiketi
    ai_model_version: str = Field(default="unversioned", alias="AI_MODEL_VERSION")
    ai_model_metadata_path: Optional[str] = Field(
        default=None,
        alias="AI_MODEL_METADATA_PATH",
    )

    # Runtime ortamında model doğrulanamazsa servis açılmamalıdır.
    # Test ortamı artifact olmadan çalışabilsin diye varsayılan False;
    # canonical root .env runtime için True değerini verir.
    ai_model_required: bool = Field(
        default=False,
        alias="AI_MODEL_REQUIRED",
    )
    # cpu / cuda / mps - model handoff dokümanından gelecek
    ai_model_device: str = Field(default="cpu", alias="AI_MODEL_DEVICE")

    # --- Inference eşikleri (Adım 0 handoff'tan doğrulanacak) ---
    confidence_threshold: float = Field(
        default=0.50,
        alias="CONFIDENCE_THRESHOLD",
    )

    welding_confidence_threshold: float = Field(
        default=0.25,
        alias="WELDING_CONFIDENCE_THRESHOLD",
    )

    gloves_confidence_threshold: float = Field(
        default=0.35,
        alias="GLOVES_CONFIDENCE_THRESHOLD",
    )


    welding_mask_confidence_threshold: float = Field(
        default=0.40,
        alias="WELDING_MASK_CONFIDENCE_THRESHOLD",
        ge=0.0,
        le=1.0,
    )

    non_gloves_confidence_threshold: float = Field(
        default=0.30,
        alias="NON_GLOVES_CONFIDENCE_THRESHOLD",
    )

    iou_threshold: float = Field(
        default=0.45,
        alias="IOU_THRESHOLD",
    )

    # --- Backend entegrasyonu (Adım 4) ---
    backend_base_url: str = Field(default="http://localhost:8080", alias="BACKEND_BASE_URL")
    backend_detections_path: str = Field(
        default="/internal/v1/detections", alias="BACKEND_DETECTIONS_PATH"
    )
    internal_api_key: Optional[str] = Field(default=None, alias="INTERNAL_API_KEY")
    backend_request_timeout_seconds: float = Field(
        default=2.0, alias="BACKEND_REQUEST_TIMEOUT_SECONDS"
    )

    # --- Backend'in kabul ettiği label listesi (vizör YOK) ---
    supported_labels: tuple[str, ...] = (
      "person",
      "gloves",
      "non_gloves",
      "non_welding_jacket",
      "non_welding_mask",
      "welding",
      "welding_apron",
      "welding_jacket",
      "welding_mask",
    )

    # --- Class mapping (Adım 0 handoff'tan doldurulacak) ---
    ai_class_mapping_path: Optional[str] = Field(
        default="config/class_mapping.json", alias="AI_CLASS_MAPPING_PATH"
    )

    @property
    def backend_detections_url(self) -> str:
        return f"{self.backend_base_url.rstrip('/')}{self.backend_detections_path}"


@lru_cache
def get_settings() -> Settings:
    """Settings tekil (singleton) olarak cache'lenir; her istekte yeniden okunmaz."""
    return Settings()
