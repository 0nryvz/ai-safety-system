import asyncio

from app.domain.frame import FramePacket


class SessionFrameQueue:
    def __init__(
            self,
            camera_id: str,
            session_id: str,
            max_frames: int,
    ) -> None:
        if max_frames < 1:
            raise ValueError("max_frames must be greater than zero")

        self.camera_id = camera_id
        self.session_id = session_id
        self._queue: asyncio.Queue[FramePacket] = asyncio.Queue(
            maxsize=max_frames,
        )

    def enqueue(
            self,
            frame: FramePacket,
    ) -> FramePacket | None:
        self._validate_ownership(frame)

        dropped_frame: FramePacket | None = None

        if self._queue.full():
            dropped_frame = self._queue.get_nowait()
            self._queue.task_done()

        self._queue.put_nowait(frame)

        return dropped_frame

    async def dequeue(self) -> FramePacket:
        return await self._queue.get()

    def mark_processed(self) -> None:
        self._queue.task_done()

    def clear(self) -> int:
        cleared_count = 0

        while True:
            try:
                self._queue.get_nowait()
            except asyncio.QueueEmpty:
                break

            self._queue.task_done()
            cleared_count += 1

        return cleared_count

    @property
    def depth(self) -> int:
        return self._queue.qsize()

    @property
    def capacity(self) -> int:
        return self._queue.maxsize

    def _validate_ownership(
            self,
            frame: FramePacket,
    ) -> None:
        if (
                frame.camera_id != self.camera_id
                or frame.session_id != self.session_id
        ):
            raise ValueError(
                "Frame does not belong to this session queue"
            )