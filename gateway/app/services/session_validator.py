from dataclasses import dataclass
from typing import Protocol


@dataclass(frozen=True, slots=True)
class SessionValidationResult:
    is_valid: bool
    camera_active: bool
    reason: str | None = None


class CameraSessionValidator(Protocol):
    async def validate_open_session(
            self,
            camera_id: str,
            session_id: str,
            session_token: str,
    ) -> SessionValidationResult:
        ...