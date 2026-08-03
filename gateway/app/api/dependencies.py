from functools import lru_cache

from app.services.session_manager import SessionManager


@lru_cache
def get_session_manager() -> SessionManager:
    return SessionManager()