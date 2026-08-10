import asyncio
from dataclasses import dataclass
from datetime import datetime, timedelta

from app.domain.frame import FramePacket


class SessionAIFrameSamplerConflictError(RuntimeError):
    """Sampler session identity belongs to another camera."""


@dataclass(frozen=True, slots=True)
class SessionAIFrameSamplerStats:
    sampled_frame_count: int


@dataclass(slots=True)
class SessionAIFrameSamplingState:
    camera_id: str
    session_id: str
    sample_interval: timedelta
    last_selected_at: datetime | None = None


class SessionAIFrameSampler:
    def __init__(
            self,
            sample_interval: timedelta = timedelta(milliseconds=333),
    ) -> None:
        if sample_interval <= timedelta(0):
            raise ValueError("sample_interval must be greater than zero")

        self._sample_interval = sample_interval
        self._states: dict[str, SessionAIFrameSamplingState] = {}
        self._lock = asyncio.Lock()
        self._sampled_frame_count = 0

    async def offer_frame(
            self,
            frame: FramePacket,
    ) -> FramePacket | None:
        async with self._lock:
            state = self._get_or_create_state(
                camera_id=frame.camera_id,
                session_id=frame.session_id,
            )

            if self._is_selected(
                    frame_captured_at=frame.captured_at,
                    last_selected_at=state.last_selected_at,
                    sample_interval=state.sample_interval,
            ):
                state.last_selected_at = frame.captured_at
                self._sampled_frame_count += 1
                return frame

            return None

    async def clear_session(
            self,
            camera_id: str,
            session_id: str,
    ) -> bool:
        async with self._lock:
            state = self._states.get(session_id)

            if state is None:
                return False

            if state.camera_id != camera_id:
                raise SessionAIFrameSamplerConflictError(
                    f"Sampler state for session '{session_id}' belongs "
                    f"to another camera"
                )

            del self._states[session_id]
            return True

    async def stats(self) -> SessionAIFrameSamplerStats:
        async with self._lock:
            return SessionAIFrameSamplerStats(
                sampled_frame_count=self._sampled_frame_count,
            )

    async def clear(self) -> int:
        async with self._lock:
            cleared_count = len(self._states)
            self._states.clear()
            return cleared_count


    @staticmethod
    def _is_selected(
            frame_captured_at: datetime,
            last_selected_at: datetime | None,
            sample_interval: timedelta,
    ) -> bool:
        if last_selected_at is None:
            return True

        return (frame_captured_at - last_selected_at) >= sample_interval

    def _get_or_create_state(
            self,
            camera_id: str,
            session_id: str,
    ) -> SessionAIFrameSamplingState:
        existing_state = self._states.get(session_id)

        if existing_state is not None:
            if existing_state.camera_id != camera_id:
                raise SessionAIFrameSamplerConflictError(
                    f"Sampler state for session '{session_id}' belongs "
                    f"to another camera"
                )

            return existing_state

        state = SessionAIFrameSamplingState(
            camera_id=camera_id,
            session_id=session_id,
            sample_interval=self._sample_interval,
        )
        self._states[session_id] = state

        return state