import asyncio
from contextlib import suppress
from dataclasses import dataclass, field

from app.domain.frame import FramePacket
from app.services.ai_frame_client import AIFrameClient


class SessionAIFrameDispatchWorkerConflictError(RuntimeError):
    """AI dispatch worker session identity belongs to another camera."""


@dataclass(frozen=True, slots=True)
class SessionAIFrameDispatchWorkerStats:
    dispatched_frame_count: int
    dropped_pending_frame_count: int
    send_error_count: int


@dataclass(slots=True)
class SessionAIFramePendingSlot:
    _pending_frame: FramePacket | None = None
    _event: asyncio.Event = field(default_factory=asyncio.Event)
    _lock: asyncio.Lock = field(default_factory=asyncio.Lock)

    async def offer_frame(
            self,
            frame: FramePacket,
    ) -> bool:
        async with self._lock:
            had_pending_frame = self._pending_frame is not None
            self._pending_frame = frame
            self._event.set()
            return had_pending_frame

    async def take_frame(self) -> FramePacket:
        while True:
            await self._event.wait()

            async with self._lock:
                frame = self._pending_frame
                self._pending_frame = None
                self._event.clear()

            if frame is not None:
                return frame

    async def clear(self) -> bool:
        async with self._lock:
            had_pending_frame = self._pending_frame is not None
            self._pending_frame = None
            self._event.clear()
            return had_pending_frame


@dataclass(frozen=True, slots=True)
class SessionAIFrameDispatchWorker:
    camera_id: str
    session_id: str
    task: asyncio.Task[None]
    pending_slot: SessionAIFramePendingSlot


class SessionAIFrameDispatchWorkerCoordinator:
    def __init__(
            self,
            ai_frame_client: AIFrameClient,
    ) -> None:
        self._workers: dict[str, SessionAIFrameDispatchWorker] = {}
        self._lock = asyncio.Lock()
        self._ai_frame_client = ai_frame_client
        self._dispatched_frame_count = 0
        self._dropped_pending_frame_count = 0
        self._send_error_count = 0

    async def start_worker(
            self,
            camera_id: str,
            session_id: str,
    ) -> bool:
        async with self._lock:
            existing_worker = self._workers.get(session_id)

            if existing_worker is not None:
                self._validate_ownership(
                    worker=existing_worker,
                    camera_id=camera_id,
                )

                if not existing_worker.task.done():
                    return False

                self._consume_task_result(existing_worker.task)
                del self._workers[session_id]

            pending_slot = SessionAIFramePendingSlot()
            task = asyncio.create_task(
                self._run_worker(pending_slot=pending_slot)
            )
            task.add_done_callback(self._consume_task_result_on_done)

            self._workers[session_id] = SessionAIFrameDispatchWorker(
                camera_id=camera_id,
                session_id=session_id,
                task=task,
                pending_slot=pending_slot,
            )

            return True

    async def offer_frame(
            self,
            frame: FramePacket,
    ) -> bool:
        async with self._lock:
            worker = self._workers.get(frame.session_id)

            if worker is None:
                return False

            self._validate_ownership(
                worker=worker,
                camera_id=frame.camera_id,
            )

            pending_slot = worker.pending_slot

        had_pending_frame = await pending_slot.offer_frame(frame)

        if had_pending_frame:
            self._dropped_pending_frame_count += 1

        return True

    async def stop_worker(
            self,
            camera_id: str,
            session_id: str,
    ) -> bool:
        async with self._lock:
            worker = self._workers.get(session_id)

            if worker is None:
                return False

            self._validate_ownership(
                worker=worker,
                camera_id=camera_id,
            )

            del self._workers[session_id]

        await self._stop_task(worker.task)
        await worker.pending_slot.clear()
        return True

    async def active_worker_count(self) -> int:
        async with self._lock:
            self._remove_finished_workers()
            return len(self._workers)

    async def stats(self) -> SessionAIFrameDispatchWorkerStats:
        async with self._lock:
            self._remove_finished_workers()

            return SessionAIFrameDispatchWorkerStats(
                dispatched_frame_count=self._dispatched_frame_count,
                dropped_pending_frame_count=(
                    self._dropped_pending_frame_count
                ),
                send_error_count=self._send_error_count,
            )

    async def clear(self) -> int:
        async with self._lock:
            workers = tuple(self._workers.values())
            self._workers.clear()

        for worker in workers:
            with suppress(Exception):
                await self._stop_task(worker.task)

            with suppress(Exception):
                await worker.pending_slot.clear()

        return len(workers)

    async def _run_worker(
            self,
            pending_slot: SessionAIFramePendingSlot,
    ) -> None:
        while True:
            frame = await pending_slot.take_frame()

            try:
                await self._ai_frame_client.send_frame(frame)
                self._dispatched_frame_count += 1
            except asyncio.CancelledError:
                raise
            except Exception:
                self._send_error_count += 1

    async def _stop_task(
            self,
            task: asyncio.Task[None],
    ) -> None:
        if not task.done():
            task.cancel()

        try:
            await task
        except asyncio.CancelledError:
            pass
        except Exception:
            pass

        self._consume_task_result(task)

    def _remove_finished_workers(self) -> None:
        finished_session_ids = [
            session_id
            for session_id, worker in self._workers.items()
            if worker.task.done()
        ]

        for session_id in finished_session_ids:
            worker = self._workers.pop(session_id)
            self._consume_task_result(worker.task)

    @staticmethod
    def _consume_task_result(
            task: asyncio.Task[None],
    ) -> None:
        if not task.done() or task.cancelled():
            return

        with suppress(asyncio.CancelledError, asyncio.InvalidStateError):
            task.exception()

    @staticmethod
    def _consume_task_result_on_done(
            task: asyncio.Task[None],
    ) -> None:
        SessionAIFrameDispatchWorkerCoordinator._consume_task_result(task)

    @staticmethod
    def _validate_ownership(
            worker: SessionAIFrameDispatchWorker,
            camera_id: str,
    ) -> None:
        if worker.camera_id != camera_id:
            raise SessionAIFrameDispatchWorkerConflictError(
                f"AI dispatch worker for session "
                f"'{worker.session_id}' belongs to another camera"
            )
