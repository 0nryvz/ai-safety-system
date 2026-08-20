import pytest

from app.services.detection_mapper import (
    UnsupportedLabelError,
    map_model_label_to_backend_label,
    normalize_and_clamp_bbox,
)


def test_normalize_bbox_640x480():
    # 640x480 piksel bbox -> normalize
    result = normalize_and_clamp_bbox(
        x_px=64, y_px=48, width_px=320, height_px=240, frame_width=640, frame_height=480
    )
    assert 0.0 <= result.x <= 1.0
    assert 0.0 <= result.y <= 1.0
    assert 0.0 <= result.width <= 1.0
    assert 0.0 <= result.height <= 1.0
    assert result.x + result.width <= 1.0
    assert result.y + result.height <= 1.0
    assert result.x == pytest.approx(0.1)
    assert result.width == pytest.approx(0.5)


def test_normalize_bbox_clamps_when_overflowing_frame():
    # kare sınırını taşan bbox -> clamp edilmeli, x+width <= 1
    result = normalize_and_clamp_bbox(
        x_px=600, y_px=400, width_px=200, height_px=200, frame_width=640, frame_height=480
    )
    assert result.x + result.width <= 1.0 + 1e-9
    assert result.y + result.height <= 1.0 + 1e-9


def test_label_mapping_supported_label():
    mapping = {"person_cls": "person", "gloves_cls": "gloves"}
    assert map_model_label_to_backend_label("person_cls", mapping) == "person"


def test_label_mapping_rejects_unmapped_class():
    with pytest.raises(UnsupportedLabelError):
        map_model_label_to_backend_label("unknown_cls", {"person_cls": "person"})


def test_label_mapping_rejects_unsupported_backend_label():
    # örn. vizör gibi backend'in kabul etmediği bir label'a eşlenirse reddedilir
    mapping = {"visor_cls": "welding_visor"}
    with pytest.raises(UnsupportedLabelError):
        map_model_label_to_backend_label("visor_cls", mapping)
def test_bbox_touching_right_bottom_edges_stays_inside_frame():
    bbox = normalize_and_clamp_bbox(
        x_px=630,
        y_px=470,
        width_px=50,
        height_px=50,
        frame_width=640,
        frame_height=480,
    )

    assert bbox.x >= 0.0
    assert bbox.y >= 0.0
    assert bbox.x + bbox.width <= 1.0
    assert bbox.y + bbox.height <= 1.0
    assert bbox.width > 0.0
    assert bbox.height > 0.0


def test_bbox_completely_outside_frame_is_rejected():
    try:
        normalize_and_clamp_bbox(
            x_px=700,
            y_px=500,
            width_px=50,
            height_px=50,
            frame_width=640,
            frame_height=480,
        )
    except ValueError:
        return

    raise AssertionError("Tamamen frame dışındaki bbox reddedilmeliydi")