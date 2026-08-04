import pytest

from app.api.dependencies import (
    get_camera_session_lifecycle_notifier,
    get_camera_session_validator,
    get_session_frame_queue_manager,
    get_session_manager,
)
from app.core.config import get_settings
from app.main import app


CACHED_DEPENDENCIES = (
    get_settings,
    get_session_manager,
    get_camera_session_validator,
    get_camera_session_lifecycle_notifier,
    get_session_frame_queue_manager,
)


@pytest.fixture(autouse=True)
def reset_gateway_test_state():
    app.dependency_overrides.clear()

    for dependency in CACHED_DEPENDENCIES:
        dependency.cache_clear()

    yield

    app.dependency_overrides.clear()

    for dependency in CACHED_DEPENDENCIES:
        dependency.cache_clear()