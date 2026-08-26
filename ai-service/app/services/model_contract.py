"""Model artifact metadata ve 9-class sözleşmesi doğrulaması."""

from __future__ import annotations

import hashlib
import json
from hmac import compare_digest
from pathlib import Path
from typing import Any, Mapping, Sequence


CANONICAL_LABELS: tuple[str, ...] = (
    "person",
    "gloves",
    "welding_jacket",
    "welding_apron",
    "welding_mask",
    "welding",
    "non_welding_mask",
    "non_welding_jacket",
    "non_gloves",
)


class ModelContractError(RuntimeError):
    """Model artifact veya metadata sözleşmesi geçersiz."""


def validate_model_contract(
    *,
    model_path: str,
    metadata_path: str,
    configured_model_version: str,
    model_names: Mapping[int, str] | Sequence[str],
) -> dict[str, Any]:
    """Artifact hash, version, class isimleri ve index remap'i doğrular."""

    artifact = Path(model_path)
    metadata_file = Path(metadata_path)

    if not artifact.is_file():
        raise ModelContractError(
            f"Model artifact bulunamadı: {artifact}"
        )

    if not metadata_file.is_file():
        raise ModelContractError(
            f"Model metadata bulunamadı: {metadata_file}"
        )

    metadata = _read_metadata(metadata_file)

    metadata_version = str(
        metadata.get("modelVersion", "")
    ).strip()

    if not metadata_version:
        raise ModelContractError(
            "Metadata modelVersion alanı boş."
        )

    if metadata_version != configured_model_version:
        raise ModelContractError(
            "Model version sözleşmesi uyuşmuyor. "
            f"Config={configured_model_version!r}, "
            f"metadata={metadata_version!r}"
        )

    expected_hash = str(
        metadata.get("sha256", "")
    ).strip().lower()

    if len(expected_hash) != 64:
        raise ModelContractError(
            "Metadata sha256 alanı geçersiz."
        )

    actual_hash = _calculate_sha256(artifact)

    if not compare_digest(actual_hash, expected_hash):
        raise ModelContractError(
            "Model artifact SHA-256 uyuşmuyor. "
            f"Beklenen={expected_hash}, gerçek={actual_hash}"
        )

    actual_names = _ordered_model_names(model_names)
    metadata_names = _metadata_model_names(metadata)

    normalized_actual = tuple(
        name.casefold()
        for name in actual_names
    )
    normalized_metadata = tuple(
        name.casefold()
        for name in metadata_names
    )

    if normalized_actual != normalized_metadata:
        raise ModelContractError(
            "Model names metadata ile uyuşmuyor. "
            f"Model={normalized_actual}, "
            f"metadata={normalized_metadata}"
        )

    canonical_names = tuple(
        str(label).strip().casefold()
        for label in metadata.get(
            "canonical_class_order",
            [],
        )
    )

    if canonical_names != CANONICAL_LABELS:
        raise ModelContractError(
            "Canonical 9-class sözleşmesi uyuşmuyor. "
            f"Beklenen={CANONICAL_LABELS}, "
            f"metadata={canonical_names}"
        )

    if (
        len(normalized_actual) != len(CANONICAL_LABELS)
        or len(set(normalized_actual)) != len(CANONICAL_LABELS)
        or set(normalized_actual) != set(CANONICAL_LABELS)
    ):
        raise ModelContractError(
            "Model tam ve benzersiz canonical 9 label "
            "kümesini içermiyor."
        )

    actual_remap = _metadata_index_remap(metadata)

    expected_remap = {
        raw_index: CANONICAL_LABELS.index(label)
        for raw_index, label in enumerate(normalized_actual)
    }

    if actual_remap != expected_remap:
        raise ModelContractError(
            "Metadata index_remap sözleşmesi geçersiz. "
            f"Beklenen={expected_remap}, "
            f"metadata={actual_remap}"
        )

    return metadata


def _read_metadata(
    metadata_path: Path,
) -> dict[str, Any]:
    try:
        content = metadata_path.read_text(
            encoding="utf-8",
        )
        metadata = json.loads(content)
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ModelContractError(
            f"Model metadata okunamadı: {metadata_path}"
        ) from exc

    if not isinstance(metadata, dict):
        raise ModelContractError(
            "Model metadata JSON object olmalıdır."
        )

    return metadata


def _calculate_sha256(
    artifact_path: Path,
) -> str:
    digest = hashlib.sha256()

    try:
        with artifact_path.open("rb") as artifact_file:
            for chunk in iter(
                lambda: artifact_file.read(1024 * 1024),
                b"",
            ):
                digest.update(chunk)
    except OSError as exc:
        raise ModelContractError(
            f"Model artifact okunamadı: {artifact_path}"
        ) from exc

    return digest.hexdigest()


def _ordered_model_names(
    model_names: Mapping[int, str] | Sequence[str],
) -> tuple[str, ...]:
    if isinstance(model_names, Mapping):
        try:
            ordered_ids = sorted(
                int(class_id)
                for class_id in model_names
            )

            expected_ids = list(
                range(len(ordered_ids))
            )

            if ordered_ids != expected_ids:
                raise ModelContractError(
                    "Model class ID değerleri kesintisiz "
                    f"değil: {ordered_ids}"
                )

            return tuple(
                str(
                    model_names[class_id]
                    if class_id in model_names
                    else model_names[str(class_id)]
                ).strip()
                for class_id in ordered_ids
            )
        except (KeyError, TypeError, ValueError) as exc:
            raise ModelContractError(
                "Model names sözleşmesi okunamadı."
            ) from exc

    if isinstance(model_names, (str, bytes)):
        raise ModelContractError(
            "Model names liste veya dictionary olmalıdır."
        )

    return tuple(
        str(label).strip()
        for label in model_names
    )


def _metadata_model_names(
    metadata: dict[str, Any],
) -> tuple[str, ...]:
    raw_names = metadata.get("raw_model_names")

    if not isinstance(raw_names, dict):
        raise ModelContractError(
            "Metadata raw_model_names alanı geçersiz."
        )

    try:
        ids = sorted(
            int(class_id)
            for class_id in raw_names
        )

        if ids != list(range(len(ids))):
            raise ModelContractError(
                "Metadata class ID değerleri kesintisiz değil."
            )

        return tuple(
            str(raw_names[str(class_id)]).strip()
            for class_id in ids
        )
    except (KeyError, TypeError, ValueError) as exc:
        raise ModelContractError(
            "Metadata raw_model_names okunamadı."
        ) from exc


def _metadata_index_remap(
    metadata: dict[str, Any],
) -> dict[int, int]:
    raw_remap = metadata.get("index_remap")

    if not isinstance(raw_remap, dict):
        raise ModelContractError(
            "Metadata index_remap alanı geçersiz."
        )

    try:
        remap = {
            int(raw_index): int(canonical_index)
            for raw_index, canonical_index
            in raw_remap.items()
        }
    except (TypeError, ValueError) as exc:
        raise ModelContractError(
            "Metadata index_remap okunamadı."
        ) from exc

    expected_ids = set(range(len(CANONICAL_LABELS)))

    if (
        set(remap) != expected_ids
        or set(remap.values()) != expected_ids
    ):
        raise ModelContractError(
            "Metadata index_remap birebir 0..8 "
            "eşlemesi olmalıdır."
        )

    return remap