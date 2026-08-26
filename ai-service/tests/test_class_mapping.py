"""
Adım 0 handoff'tan gelen gerçek class_mapping.json'ın doğru yüklendiğini
ve backend'in kabul ettiği tüm label'ları kapsadığını doğrular.
"""
from __future__ import annotations

from pathlib import Path

from app.services.class_mapping import load_class_mapping
from app.services.detection_mapper import (
    SUPPORTED_BACKEND_LABELS,
    map_model_label_to_backend_label,
)

CLASS_MAPPING_PATH = Path(__file__).resolve().parent.parent / "config" / "class_mapping.json"


def test_class_mapping_file_loads():
    mapping = load_class_mapping(str(CLASS_MAPPING_PATH))
    assert mapping  # boş olmamalı


def test_all_model_classes_map_to_supported_backend_labels():
    mapping = load_class_mapping(str(CLASS_MAPPING_PATH))
    for model_class_name in mapping:
        backend_label = map_model_label_to_backend_label(model_class_name, mapping)
        assert backend_label in SUPPORTED_BACKEND_LABELS


def test_no_visor_class_in_mapping():
    # Görev planı: vizör sınıfı asla eklenmez.
    mapping = load_class_mapping(str(CLASS_MAPPING_PATH))
    for backend_label in mapping.values():
        assert "visor" not in backend_label.lower()


def test_expected_nine_classes_present():
    mapping = load_class_mapping(str(CLASS_MAPPING_PATH))
    expected = {
        "Person",
        "gloves",
        "non_gloves",
        "non_welding_jacket",
        "non_welding_mask",
        "welding",
        "welding_apron",
        "welding_jacket",
        "welding_mask",
    }

    assert set(mapping.keys()) == expected

def test_non_classes_are_preserved():
    mapping = load_class_mapping(str(CLASS_MAPPING_PATH))

    assert mapping["non_gloves"] == "non_gloves"
    assert mapping["non_welding_jacket"] == "non_welding_jacket"
    assert mapping["non_welding_mask"] == "non_welding_mask"
