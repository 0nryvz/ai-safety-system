import pytest
from fastapi.testclient import TestClient

import app.main as main_module
from app.core.config import Settings
from app.services.model_runner import ModelRunner


def test_required_invalid_model_aborts_startup(
    monkeypatch,
):
    settings = Settings(
        AI_MODEL_REQUIRED=True,
        AI_MODEL_PATH="missing.pt",
        AI_MODEL_METADATA_PATH="missing.json",
        AI_MODEL_VERSION="best_update_v1",
    )

    monkeypatch.setattr(
        main_module,
        "get_settings",
        lambda: settings,
    )

    def fake_failed_load(self):
        self._model = None
        self._loaded = False
        self._load_error = "test artifact contract error"

    monkeypatch.setattr(
        ModelRunner,
        "load",
        fake_failed_load,
    )

    test_app = main_module.create_app()

    with pytest.raises(
        RuntimeError,
        match="startup fail-fast",
    ):
        with TestClient(test_app):
            pass