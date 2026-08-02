import asyncio

from app.domain.session import CameraSessionContext


class SessionNotFoundError(LookupError):
    """İstenen kamera oturumu aktif session koleksiyonunda bulunamadı."""


class SessionConflictError(RuntimeError):
    """Aynı session_id farklı bir kamera tarafından kullanılmaya çalışıldı."""


class SessionManager:
    def __init__(self) -> None:
        self._sessions: dict[str, CameraSessionContext] = {}
        self._lock = asyncio.Lock()

    async def open_session(
            self,
            camera_id: str,
            session_id: str,
    ) -> tuple[CameraSessionContext, bool]:
        async with self._lock:
            existing_session = self._sessions.get(session_id)

            if existing_session is not None:
                if existing_session.camera_id != camera_id:
                    raise SessionConflictError(
                        f"Session '{session_id}' belongs to another camera"
                    )

                existing_session.heartbeat()
                return existing_session, False

            new_session = CameraSessionContext(
                camera_id=camera_id,
                session_id=session_id,
            )

            self._sessions[session_id] = new_session
            return new_session, True

    async def get_session(
            self,
            session_id: str,
    ) -> CameraSessionContext:
        async with self._lock:
            session = self._sessions.get(session_id)

            if session is None:
                raise SessionNotFoundError(
                    f"Session '{session_id}' was not found"
                )

            return session

    async def heartbeat(
            self,
            camera_id: str,
            session_id: str,
    ) -> CameraSessionContext:
        async with self._lock:
            session = self._get_owned_session(
                camera_id=camera_id,
                session_id=session_id,
            )

            session.heartbeat()
            return session

    async def register_frame(
            self,
            camera_id: str,
            session_id: str,
    ) -> CameraSessionContext:
        async with self._lock:
            session = self._get_owned_session(
                camera_id=camera_id,
                session_id=session_id,
            )

            session.register_frame()
            return session

    async def close_session(
            self,
            camera_id: str,
            session_id: str,
    ) -> bool:
        async with self._lock:
            session = self._sessions.get(session_id)

            if session is None:
                return False

            if session.camera_id != camera_id:
                raise SessionConflictError(
                    f"Session '{session_id}' belongs to another camera"
                )

            session.close()
            del self._sessions[session_id]

            return True

    async def active_session_count(self) -> int:
        async with self._lock:
            return len(self._sessions)

    async def clear(self) -> None:
        async with self._lock:
            for session in self._sessions.values():
                session.close()

            self._sessions.clear()

    def _get_owned_session(
            self,
            camera_id: str,
            session_id: str,
    ) -> CameraSessionContext:
        session = self._sessions.get(session_id)

        if session is None:
            raise SessionNotFoundError(
                f"Session '{session_id}' was not found"
            )

        if session.camera_id != camera_id:
            raise SessionConflictError(
                f"Session '{session_id}' belongs to another camera"
            )

        return session