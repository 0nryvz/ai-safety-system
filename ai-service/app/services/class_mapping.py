"""
Model class id/name -> backend label eşlemesini bir JSON dosyasından yükler.

Kullanıcıdan (Adım 0 handoff) class listesi geldiğinde tek yapılacak şey
config/class_mapping.json dosyasını doldurmak; kod tarafında değişiklik
gerekmez.

Beklenen format:
{
  "0": "person",
  "1": "welding",
  "2": "gloves"
}
(anahtar model class id'si VEYA class ismi olabilir - ModelRunner hangisini
 kullanıyorsa onunla tutarlı olmalı.)
"""
from __future__ import annotations

import json
import logging
from pathlib import Path

logger = logging.getLogger(__name__)


def load_class_mapping(path: str | None) -> dict[str, str]:
    if not path:
        logger.warning(
            "Class mapping tanımlı değil (AI_CLASS_MAPPING_PATH boş) - "
            "Adım 0 handoff'tan class listesi gelene kadar tüm inference "
            "label'ları UnsupportedLabelError ile reddedilecek."
        )
        return {}

    file_path = Path(path)
    if not file_path.exists():
        logger.warning("Class mapping dosyası bulunamadı: %s", path)
        return {}

    with file_path.open("r", encoding="utf-8") as f:
        raw = json.load(f)

    if not isinstance(raw, dict):
        raise ValueError("Class mapping dosyası bir JSON object olmalı: {'0': 'person', ...}")

    return {str(k): str(v) for k, v in raw.items()}
