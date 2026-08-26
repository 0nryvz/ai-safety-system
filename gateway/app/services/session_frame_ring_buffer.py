import asyncio
from collections import deque
from collections.abc import Callable
from dataclasses import dataclass, replace
from datetime import datetime, timedelta, timezone

from app.domain.frame import FramePacket


class FrameRingBufferError(RuntimeError):
    """Base error for ring buffer operations."""


class FrameRingBufferConflictError(FrameRingBufferError):
    """Frame or operation does not belong to the buffer owner."""


class FrameRingBufferValidationError(FrameRingBufferError):
    """Frame metadata or buffer limits are invalid."""


class FrameRingBufferFrameTooLargeError(FrameRingBufferValidationError):
    """Single frame exceeds the configured byte limit."""


class FrameRingBufferOutOfOrderError(FrameRingBufferValidationError):
    """Frame timestamp would break chronological buffer ordering."""


class FrameRingBufferClosedError(FrameRingBufferError):
    """Ring buffer is already closed and does not accept new frames."""


@dataclass(frozen=True, slots=True)
class FrameRingBufferAppendResult:
    accepted: bool
    frame_count: int
    total_bytes: int
    evicted_frame_count: int
    evicted_bytes: int


@dataclass(frozen=True, slots=True)
class FrameRingBufferClearResult:
    cleared_frame_count: int
    cleared_bytes: int


@dataclass(frozen=True, slots=True)
class FrameRingBufferStats:
    camera_id: str
    session_id: str
    frame_count: int
    total_bytes: int
    buffer_seconds: int
    max_frames: int
    max_bytes: int
    oldest_frame_timestamp: datetime | None
    newest_frame_timestamp: datetime | None
    total_evicted_frame_count: int
    total_evicted_bytes: int


class SessionFrameRingBuffer:
    def __init__(
            self,
            camera_id: str,
            session_id: str,
            buffer_seconds: int,
            max_frames: int,
            max_bytes: int,
    ) -> None:
        if buffer_seconds < 5 or buffer_seconds > 10:
            raise ValueError(
                "buffer_seconds must be between 5 and 10"
            )

        if max_frames < 1:
            raise ValueError(
                "max_frames must be greater than zero"
            )

        if max_bytes < 1:
            raise ValueError(
                "max_bytes must be greater than zero"
            )

        self.camera_id = camera_id
        self.session_id = session_id
        self.buffer_seconds = buffer_seconds
        self.max_frames = max_frames
        self.max_bytes = max_bytes

        self._frames: deque[FramePacket] = deque()
        self._total_bytes = 0
        self._total_evicted_frame_count = 0
        self._total_evicted_bytes = 0
        self._is_closed = False
        self._lock = asyncio.Lock()

    async def append(
            self,
            frame: FramePacket,
    ) -> FrameRingBufferAppendResult:
        self._validate_ownership(frame)
        captured_at = self._normalize_timestamp(frame.captured_at)

        if frame.size_bytes > self.max_bytes:
            raise FrameRingBufferFrameTooLargeError(
                "Frame exceeds ring buffer max_bytes"
            )

        normalized_frame = replace(
            frame,
            captured_at=captured_at,
        )

        async with self._lock:
            if self._is_closed:
                raise FrameRingBufferClosedError(
                    "Ring buffer is closed"
                )

            if (
                    self._frames
                    and captured_at < self._frames[-1].captured_at
            ):
                raise FrameRingBufferOutOfOrderError(
                    "Frame timestamp is older than the newest buffered frame"
                )

            self._frames.append(normalized_frame)
            self._total_bytes += normalized_frame.size_bytes

            evicted_frame_count = 0
            evicted_bytes = 0

            cutoff = captured_at - timedelta(
                seconds=self.buffer_seconds,
            )
            evicted = self._evict_while(
                lambda: bool(self._frames)
                and self._frames[0].captured_at < cutoff,
            )
            evicted_frame_count += evicted.cleared_frame_count
            evicted_bytes += evicted.cleared_bytes

            evicted = self._evict_while(
                lambda: len(self._frames) > self.max_frames,
            )
            evicted_frame_count += evicted.cleared_frame_count
            evicted_bytes += evicted.cleared_bytes

            evicted = self._evict_while(
                lambda: self._total_bytes > self.max_bytes,
            )
            evicted_frame_count += evicted.cleared_frame_count
            evicted_bytes += evicted.cleared_bytes

            self._total_evicted_frame_count += evicted_frame_count
            self._total_evicted_bytes += evicted_bytes

            return FrameRingBufferAppendResult(
                accepted=True,
                frame_count=len(self._frames),
                total_bytes=self._total_bytes,
                evicted_frame_count=evicted_frame_count,
                evicted_bytes=evicted_bytes,
            )

    async def snapshot(self) -> tuple[FramePacket, ...]:
        async with self._lock:
            return tuple(self._frames)

    async def clear(self) -> FrameRingBufferClearResult:
        async with self._lock:
            cleared_frame_count = len(self._frames)
            cleared_bytes = self._total_bytes

            self._frames.clear()
            self._total_bytes = 0

            return FrameRingBufferClearResult(
                cleared_frame_count=cleared_frame_count,
                cleared_bytes=cleared_bytes,
            )

    async def close(self) -> FrameRingBufferClearResult:
        async with self._lock:
            if self._is_closed:
                return FrameRingBufferClearResult(
                    cleared_frame_count=0,
                    cleared_bytes=0,
                )

            self._is_closed = True

            cleared_frame_count = len(self._frames)
            cleared_bytes = self._total_bytes

            self._frames.clear()
            self._total_bytes = 0

            return FrameRingBufferClearResult(
                cleared_frame_count=cleared_frame_count,
                cleared_bytes=cleared_bytes,
            )

    async def stats(self) -> FrameRingBufferStats:
        async with self._lock:
            oldest_frame_timestamp = (
                self._frames[0].captured_at
                if self._frames
                else None
            )
            newest_frame_timestamp = (
                self._frames[-1].captured_at
                if self._frames
                else None
            )

            return FrameRingBufferStats(
                camera_id=self.camera_id,
                session_id=self.session_id,
                frame_count=len(self._frames),
                total_bytes=self._total_bytes,
                buffer_seconds=self.buffer_seconds,
                max_frames=self.max_frames,
                max_bytes=self.max_bytes,
                oldest_frame_timestamp=oldest_frame_timestamp,
                newest_frame_timestamp=newest_frame_timestamp,
                total_evicted_frame_count=(
                    self._total_evicted_frame_count
                ),
                total_evicted_bytes=self._total_evicted_bytes,
            )

    def _evict_while(
            self,
            predicate: Callable[[], bool],
    ) -> FrameRingBufferClearResult:
        evicted_frame_count = 0
        evicted_bytes = 0

        while predicate():
            evicted_frame = self._frames.popleft()
            evicted_frame_count += 1
            evicted_bytes += evicted_frame.size_bytes
            self._total_bytes -= evicted_frame.size_bytes

        return FrameRingBufferClearResult(
            cleared_frame_count=evicted_frame_count,
            cleared_bytes=evicted_bytes,
        )

    def _validate_ownership(
            self,
            frame: FramePacket,
    ) -> None:
        if frame.camera_id != self.camera_id:
            raise FrameRingBufferConflictError(
                "Frame belongs to another camera"
            )

        if frame.session_id != self.session_id:
            raise FrameRingBufferConflictError(
                "Frame belongs to another session"
            )

    @staticmethod
    def _normalize_timestamp(
            captured_at: datetime,
    ) -> datetime:
        if (
                captured_at.tzinfo is None
                or captured_at.utcoffset() is None
        ):
            raise FrameRingBufferValidationError(
                "Frame timestamp must include timezone"
            )

        return captured_at.astimezone(timezone.utc)
