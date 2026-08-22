"""
GEÇİCİ DEBUG — dosya tetikli 5 örnek yakalama.

Trigger: ai-service/debug_frames/capture_5.trigger
AI Worker restart gerekmez; her uygun inference'da trigger var mı diye bakılır.

Inference ve Backend POST davranışını değiştirmez. Hata olursa yutulur.
"""
from __future__ import annotations

import json
import logging
import threading
import time
from pathlib import Path

logger = logging.getLogger(__name__)

_AI_SERVICE_ROOT = Path(__file__).resolve().parents[1]
DEBUG_DIR = _AI_SERVICE_ROOT / "debug_frames"
TRIGGER_PATH = DEBUG_DIR / "capture_5.trigger"

SAMPLE_COUNT = 5
SAMPLE_INTERVAL_S = 2.0

_lock = threading.Lock()
_session_active = False
_samples_saved = 0
_last_sample_at: float | None = None


def reset_debug_capture_state() -> None:
    """Test / yeni süreç için bellek durumunu sıfırla."""
    with _lock:
        _reset_session()


def maybe_capture_debug_sample(jpeg_bytes: bytes, payload_body: dict) -> None:
    """
    Trigger varsa sıradaki örneği kaydet. ``payload_body`` BackendClient.send
    içindeki ``json=body`` ile AYNI dict olmalıdır; burada yeniden kurulmaz.
    """
    try:
        _maybe_capture(jpeg_bytes, payload_body)
    except Exception as exc:  # noqa: BLE001 - debug asla inference'ı bozmaz
        logger.warning("DEBUG SAMPLE FAILED (inference continues): %s", exc)


def _maybe_capture(jpeg_bytes: bytes, payload_body: dict) -> None:
    global _session_active, _samples_saved, _last_sample_at

    with _lock:
        if not TRIGGER_PATH.is_file():
            _reset_session()
            return

        if not _session_active:
            _start_session()

        if _samples_saved >= SAMPLE_COUNT:
            return

        now = time.monotonic()
        if (
            _last_sample_at is not None
            and (now - _last_sample_at) < SAMPLE_INTERVAL_S
        ):
            return

        index = _samples_saved + 1
        _write_sample(index, jpeg_bytes, payload_body)
        _samples_saved = index
        _last_sample_at = now

        message = f"DEBUG SAMPLE {index}/{SAMPLE_COUNT} SAVED"
        print(message, flush=True)
        logger.warning(message)

        if index >= SAMPLE_COUNT:
            _finish_session()


def _start_session() -> None:
    global _session_active, _samples_saved, _last_sample_at

    DEBUG_DIR.mkdir(parents=True, exist_ok=True)
    _clear_old_samples()
    _session_active = True
    _samples_saved = 0
    _last_sample_at = None


def _finish_session() -> None:
    try:
        TRIGGER_PATH.unlink(missing_ok=True)
    except OSError as exc:
        logger.warning("DEBUG SAMPLE trigger silinemedi: %s", exc)
    _reset_session()


def _reset_session() -> None:
    global _session_active, _samples_saved, _last_sample_at

    _session_active = False
    _samples_saved = 0
    _last_sample_at = None


def _clear_old_samples() -> None:
    for index in range(1, SAMPLE_COUNT + 1):
        for path in (
            DEBUG_DIR / f"sample_{index:02d}.jpg",
            DEBUG_DIR / f"sample_{index:02d}_payload.json",
        ):
            try:
                path.unlink(missing_ok=True)
            except OSError:
                pass


def _write_sample(index: int, jpeg_bytes: bytes, payload_body: dict) -> None:
    DEBUG_DIR.mkdir(parents=True, exist_ok=True)
    jpg_path = DEBUG_DIR / f"sample_{index:02d}.jpg"
    json_path = DEBUG_DIR / f"sample_{index:02d}_payload.json"

    jpg_path.write_bytes(jpeg_bytes)
    # httpx `json=body` varsayılan json.dumps ile aynı serileştirme.
    json_path.write_text(json.dumps(payload_body), encoding="utf-8")
