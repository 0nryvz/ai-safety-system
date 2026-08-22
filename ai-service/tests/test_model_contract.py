import hashlib
import json

import pytest

from app.services.model_contract import (
    CANONICAL_LABELS,
    ModelContractError,
    validate_model_contract,
)


RAW_MODEL_NAMES = {
    0: "Person",
    1: "gloves",
    2: "non_gloves",
    3: "non_welding_jacket",
    4: "non_welding_mask",
    5: "welding",
    6: "welding_apron",
    7: "welding_jacket",
    8: "welding_mask",
}

EXPECTED_REMAP = {
    0: 0,
    1: 1,
    2: 8,
    3: 7,
    4: 6,
    5: 5,
    6: 3,
    7: 2,
    8: 4,
}


def create_artifact_and_metadata(tmp_path):
    artifact_path = tmp_path / "best.pt"
    artifact_content = b"test-model-artifact"
    artifact_path.write_bytes(artifact_content)

    artifact_hash = hashlib.sha256(
        artifact_content
    ).hexdigest()

    metadata = {
        "modelVersion": "best_update_v1",
        "sha256": artifact_hash,
        "raw_model_names": {
            str(class_id): label
            for class_id, label
            in RAW_MODEL_NAMES.items()
        },
        "canonical_class_order": list(
            CANONICAL_LABELS
        ),
        "index_remap": {
            str(raw_index): canonical_index
            for raw_index, canonical_index
            in EXPECTED_REMAP.items()
        },
    }

    metadata_path = tmp_path / "model_info.json"
    metadata_path.write_text(
        json.dumps(metadata),
        encoding="utf-8",
    )

    return artifact_path, metadata_path, metadata


def validate(
    artifact_path,
    metadata_path,
    model_names=RAW_MODEL_NAMES,
    version="best_update_v1",
):
    return validate_model_contract(
        model_path=str(artifact_path),
        metadata_path=str(metadata_path),
        configured_model_version=version,
        model_names=model_names,
    )


def write_metadata(metadata_path, metadata):
    metadata_path.write_text(
        json.dumps(metadata),
        encoding="utf-8",
    )


def test_valid_artifact_contract_passes(tmp_path):
    artifact_path, metadata_path, metadata = (
        create_artifact_and_metadata(tmp_path)
    )

    result = validate(
        artifact_path,
        metadata_path,
    )

    assert result["modelVersion"] == "best_update_v1"
    assert result["sha256"] == metadata["sha256"]


def test_missing_artifact_fails(tmp_path):
    _, metadata_path, _ = (
        create_artifact_and_metadata(tmp_path)
    )

    missing_artifact = tmp_path / "missing.pt"

    with pytest.raises(
        ModelContractError,
        match="artifact bulunamadı",
    ):
        validate(
            missing_artifact,
            metadata_path,
        )


def test_missing_metadata_fails(tmp_path):
    artifact_path, _, _ = (
        create_artifact_and_metadata(tmp_path)
    )

    missing_metadata = tmp_path / "missing.json"

    with pytest.raises(
        ModelContractError,
        match="metadata bulunamadı",
    ):
        validate(
            artifact_path,
            missing_metadata,
        )


def test_wrong_sha256_fails(tmp_path):
    artifact_path, metadata_path, metadata = (
        create_artifact_and_metadata(tmp_path)
    )

    metadata["sha256"] = "0" * 64
    write_metadata(metadata_path, metadata)

    with pytest.raises(
        ModelContractError,
        match="SHA-256 uyuşmuyor",
    ):
        validate(
            artifact_path,
            metadata_path,
        )


def test_wrong_model_version_fails(tmp_path):
    artifact_path, metadata_path, _ = (
        create_artifact_and_metadata(tmp_path)
    )

    with pytest.raises(
        ModelContractError,
        match="version sözleşmesi uyuşmuyor",
    ):
        validate(
            artifact_path,
            metadata_path,
            version="wrong-version",
        )


def test_wrong_model_names_fails(tmp_path):
    artifact_path, metadata_path, _ = (
        create_artifact_and_metadata(tmp_path)
    )

    wrong_names = dict(RAW_MODEL_NAMES)
    wrong_names[2] = "wrong_label"

    with pytest.raises(
        ModelContractError,
        match="Model names metadata ile uyuşmuyor",
    ):
        validate(
            artifact_path,
            metadata_path,
            model_names=wrong_names,
        )


def test_wrong_canonical_order_fails(tmp_path):
    artifact_path, metadata_path, metadata = (
        create_artifact_and_metadata(tmp_path)
    )

    metadata["canonical_class_order"][0] = "wrong_label"
    write_metadata(metadata_path, metadata)

    with pytest.raises(
        ModelContractError,
        match="Canonical 9-class",
    ):
        validate(
            artifact_path,
            metadata_path,
        )


def test_wrong_index_remap_fails(tmp_path):
    artifact_path, metadata_path, metadata = (
        create_artifact_and_metadata(tmp_path)
    )

    metadata["index_remap"]["2"] = 2
    write_metadata(metadata_path, metadata)

    with pytest.raises(
        ModelContractError,
        match="index_remap",
    ):
        validate(
            artifact_path,
            metadata_path,
        )