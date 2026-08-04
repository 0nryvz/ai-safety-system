from hmac import compare_digest

from app.services.session_validator import SessionValidationResult


class LocalCameraSessionValidator:
    def __init__(self, expected_token: str) -> None:
        self._expected_token = expected_token

    async def validate_open_session(
            self,
            camera_id: str,
            session_id: str,
            session_token: str,
    ) -> SessionValidationResult:
        if not camera_id or not session_id:
            return SessionValidationResult(
                is_valid=False,
                camera_active=False,
                reason="INVALID_SESSION_IDENTIFIERS",
            )

        if not compare_digest(session_token, self._expected_token):
            return SessionValidationResult(
                is_valid=False,
                camera_active=False,
                reason="INVALID_SESSION_TOKEN",
            )

        return SessionValidationResult(
            is_valid=True,
            camera_active=True,
        )