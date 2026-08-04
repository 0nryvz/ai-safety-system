from functools import lru_cache

from app.core.config import get_settings
from app.infrastructure.local_session_lifecycle_notifier import (
    LocalCameraSessionLifecycleNotifier,
)
from app.infrastructure.local_session_validator import (
    LocalCameraSessionValidator,
)
from app.services.session_lifecycle_notifier import (
    CameraSessionLifecycleNotifier,
)
from app.services.session_manager import SessionManager
from app.services.session_validator import CameraSessionValidator


@lru_cache
def get_session_manager() -> SessionManager:
    return SessionManager()


@lru_cache
def get_camera_session_validator() -> CameraSessionValidator:
    settings = get_settings()

    return LocalCameraSessionValidator(
        expected_token=settings.local_session_token,
    )


@lru_cache
def get_camera_session_lifecycle_notifier(
) -> CameraSessionLifecycleNotifier:
    return LocalCameraSessionLifecycleNotifier()