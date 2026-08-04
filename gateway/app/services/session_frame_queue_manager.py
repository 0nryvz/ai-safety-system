import asyncio
from dataclasses import dataclass

from app.domain.frame import FramePacket
from app.services.session_frame_queue import SessionFrameQueue


class FrameQueueConflictError(RuntimeError):
    """Queue session kimliği başka bir kameraya aittir."""


class FrameQueueNotFoundError(LookupError):
    """İstenen session için aktif frame queue bulunamadı."""


@dataclass(frozen=True, slots=True)
class FrameEnqueueResult:
    queue_depth: int
    queue_capacity: int
    dropped_frame: FramePacket | None


class SessionFrameQueueManager:
    def __init__(self, max_frames: int) -> None:
        if max_frames < 1:
            raise ValueError(
                "max_frames must be greater than zero"
            )

        self._max_frames = max_frames
        self._queues: dict[str, SessionFrameQueue] = {}
        self._lock = asyncio.Lock()

    async def open_queue(
            self,
            camera_id: str,
            session_id: str,
    ) -> tuple[SessionFrameQueue, bool]:
        async with self._lock:
            existing_queue = self._queues.get(session_id)

            if existing_queue is not None:
                self._validate_ownership(
                    queue=existing_queue,
                    camera_id=camera_id,
                )
                return existing_queue, False

            queue = SessionFrameQueue(
                camera_id=camera_id,
                session_id=session_id,
                max_frames=self._max_frames,
            )

            self._queues[session_id] = queue
            return queue, True

    async def get_queue(
            self,
            camera_id: str,
            session_id: str,
    ) -> SessionFrameQueue:
        async with self._lock:
            queue = self._queues.get(session_id)

            if queue is None:
                raise FrameQueueNotFoundError(
                    f"Frame queue for session '{session_id}' "
                    "was not found"
                )

            self._validate_ownership(
                queue=queue,
                camera_id=camera_id,
            )

            return queue

    async def enqueue_frame(
            self,
            camera_id: str,
            session_id: str,
            frame: FramePacket,
    ) -> FrameEnqueueResult:
        async with self._lock:
            queue = self._queues.get(session_id)

            if queue is None:
                raise FrameQueueNotFoundError(
                    f"Frame queue for session '{session_id}' "
                    "was not found"
                )

            self._validate_ownership(
                queue=queue,
                camera_id=camera_id,
            )

            dropped_frame = queue.enqueue(frame)

            return FrameEnqueueResult(
                queue_depth=queue.depth,
                queue_capacity=queue.capacity,
                dropped_frame=dropped_frame,
            )

    async def close_queue(
            self,
            camera_id: str,
            session_id: str,
    ) -> int:
        async with self._lock:
            queue = self._queues.get(session_id)

            if queue is None:
                return 0

            self._validate_ownership(
                queue=queue,
                camera_id=camera_id,
            )

            del self._queues[session_id]

            return queue.clear()

    async def active_queue_count(self) -> int:
        async with self._lock:
            return len(self._queues)

    async def total_queued_frame_count(self) -> int:
        async with self._lock:
            return sum(
                queue.depth
                for queue in self._queues.values()
            )

    async def clear(self) -> int:
        async with self._lock:
            cleared_frame_count = 0

            for queue in self._queues.values():
                cleared_frame_count += queue.clear()

            self._queues.clear()
            return cleared_frame_count

    @staticmethod
    def _validate_ownership(
            queue: SessionFrameQueue,
            camera_id: str,
    ) -> None:
        if queue.camera_id != camera_id:
            raise FrameQueueConflictError(
                f"Frame queue for session "
                f"'{queue.session_id}' belongs to another camera"
            )