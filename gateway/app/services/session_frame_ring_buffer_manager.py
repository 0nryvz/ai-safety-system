import asyncio

from app.domain.frame import FramePacket
from app.services.session_frame_ring_buffer import (
    FrameRingBufferAppendResult,
    FrameRingBufferClearResult,
    FrameRingBufferConflictError,
    SessionFrameRingBuffer,
)


class FrameRingBufferNotFoundError(LookupError):
    """Requested session does not have an active ring buffer."""


class SessionFrameRingBufferManager:
    def __init__(
            self,
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

        self._buffer_seconds = buffer_seconds
        self._max_frames = max_frames
        self._max_bytes = max_bytes
        self._buffers: dict[str, SessionFrameRingBuffer] = {}
        self._lock = asyncio.Lock()

    async def open_buffer(
            self,
            camera_id: str,
            session_id: str,
    ) -> tuple[SessionFrameRingBuffer, bool]:
        async with self._lock:
            existing_buffer = self._buffers.get(session_id)

            if existing_buffer is not None:
                self._validate_ownership(
                    buffer=existing_buffer,
                    camera_id=camera_id,
                )
                return existing_buffer, False

            buffer = SessionFrameRingBuffer(
                camera_id=camera_id,
                session_id=session_id,
                buffer_seconds=self._buffer_seconds,
                max_frames=self._max_frames,
                max_bytes=self._max_bytes,
            )

            self._buffers[session_id] = buffer
            return buffer, True

    async def get_buffer(
            self,
            camera_id: str,
            session_id: str,
    ) -> SessionFrameRingBuffer:
        async with self._lock:
            buffer = self._buffers.get(session_id)

            if buffer is None:
                raise FrameRingBufferNotFoundError(
                    f"Ring buffer for session '{session_id}' was not found"
                )

            self._validate_ownership(
                buffer=buffer,
                camera_id=camera_id,
            )

            return buffer

    async def append_frame(
            self,
            camera_id: str,
            session_id: str,
            frame: FramePacket,
    ) -> FrameRingBufferAppendResult:
        buffer = await self.get_buffer(
            camera_id=camera_id,
            session_id=session_id,
        )

        return await buffer.append(frame)

    async def snapshot(
            self,
            camera_id: str,
            session_id: str,
    ) -> tuple[FramePacket, ...]:
        buffer = await self.get_buffer(
            camera_id=camera_id,
            session_id=session_id,
        )

        return await buffer.snapshot()

    async def close_buffer(
            self,
            camera_id: str,
            session_id: str,
    ) -> FrameRingBufferClearResult:
        async with self._lock:
            buffer = self._buffers.get(session_id)

            if buffer is None:
                return FrameRingBufferClearResult(
                    cleared_frame_count=0,
                    cleared_bytes=0,
                )

            self._validate_ownership(
                buffer=buffer,
                camera_id=camera_id,
            )

            del self._buffers[session_id]

        return await buffer.close()

    async def active_buffer_count(self) -> int:
        async with self._lock:
            return len(self._buffers)

    async def total_buffered_frame_count(self) -> int:
        buffers = await self._snapshot_buffers()
        total_frame_count = 0

        for buffer in buffers:
            stats = await buffer.stats()
            total_frame_count += stats.frame_count

        return total_frame_count

    async def total_buffered_bytes(self) -> int:
        buffers = await self._snapshot_buffers()
        total_bytes = 0

        for buffer in buffers:
            stats = await buffer.stats()
            total_bytes += stats.total_bytes

        return total_bytes

    async def total_evicted_frame_count(self) -> int:
        buffers = await self._snapshot_buffers()
        total_evicted_frame_count = 0

        for buffer in buffers:
            stats = await buffer.stats()
            total_evicted_frame_count += (
                stats.total_evicted_frame_count
            )

        return total_evicted_frame_count

    async def total_evicted_bytes(self) -> int:
        buffers = await self._snapshot_buffers()
        total_evicted_bytes = 0

        for buffer in buffers:
            stats = await buffer.stats()
            total_evicted_bytes += stats.total_evicted_bytes

        return total_evicted_bytes

    async def clear(self) -> FrameRingBufferClearResult:
        async with self._lock:
            buffers = tuple(self._buffers.values())
            self._buffers.clear()

        cleared_frame_count = 0
        cleared_bytes = 0

        for buffer in buffers:
            clear_result = await buffer.close()
            cleared_frame_count += clear_result.cleared_frame_count
            cleared_bytes += clear_result.cleared_bytes

        return FrameRingBufferClearResult(
            cleared_frame_count=cleared_frame_count,
            cleared_bytes=cleared_bytes,
        )

    async def _snapshot_buffers(
            self,
    ) -> tuple[SessionFrameRingBuffer, ...]:
        async with self._lock:
            return tuple(self._buffers.values())

    @staticmethod
    def _validate_ownership(
            buffer: SessionFrameRingBuffer,
            camera_id: str,
    ) -> None:
        if buffer.camera_id != camera_id:
            raise FrameRingBufferConflictError(
                f"Ring buffer for session "
                f"'{buffer.session_id}' belongs to another camera"
            )
