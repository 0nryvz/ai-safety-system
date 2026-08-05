import asyncio
from contextlib import suppress
from dataclasses import dataclass

from app.services.session_frame_queue import SessionFrameQueue
from app.services.session_frame_ring_buffer import FrameRingBufferError
from app.services.session_frame_ring_buffer_manager import (
    FrameRingBufferNotFoundError,
    SessionFrameRingBufferManager,
)


class SessionFrameIngestionWorkerConflictError(RuntimeError):
    """Worker session identity belongs to another camera."""


@dataclass(frozen=True, slots=True)
class SessionFrameIngestionWorkerStats:
    ring_buffer_error_count: int
    unexpected_error_count: int


@dataclass(frozen=True, slots=True)
class SessionFrameIngestionWorker:
    camera_id: str
    session_id: str
    task: asyncio.Task[None]


class SessionFrameIngestionWorkerCoordinator:
    def __init__(self) -> None:
        self._workers: dict[str, SessionFrameIngestionWorker] = {}
        self._lock = asyncio.Lock()
        self._ring_buffer_error_count = 0
        self._unexpected_error_count = 0

    async def start_worker(
            self,
            camera_id: str,
            session_id: str,
            queue: SessionFrameQueue,
            ring_buffer_manager: SessionFrameRingBufferManager,
    ) -> bool:
        async with self._lock:
            self._validate_queue_ownership(
                queue=queue,
                camera_id=camera_id,
                session_id=session_id,
            )

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

            task = asyncio.create_task(
                self._run_worker(
                    camera_id=camera_id,
                    session_id=session_id,
                    queue=queue,
                    ring_buffer_manager=ring_buffer_manager,
                )
            )
            task.add_done_callback(self._consume_task_result_on_done)

            self._workers[session_id] = SessionFrameIngestionWorker(
                camera_id=camera_id,
                session_id=session_id,
                task=task,
            )

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
        return True

    async def active_worker_count(self) -> int:
        async with self._lock:
            self._remove_finished_workers()
            return len(self._workers)

    async def stats(
            self,
    ) -> SessionFrameIngestionWorkerStats:
        async with self._lock:
            self._remove_finished_workers()

            return SessionFrameIngestionWorkerStats(
                ring_buffer_error_count=self._ring_buffer_error_count,
                unexpected_error_count=self._unexpected_error_count,
            )

    async def clear(self) -> int:
        async with self._lock:
            workers = tuple(self._workers.values())
            self._workers.clear()

        for worker in workers:
            try:
                await self._stop_task(worker.task)
            except Exception:
                continue

        return len(workers)

    async def _run_worker(
            self,
            camera_id: str,
            session_id: str,
            queue: SessionFrameQueue,
            ring_buffer_manager: SessionFrameRingBufferManager,
    ) -> None:
        while True:
            frame = await queue.dequeue()

            try:
                await ring_buffer_manager.append_frame(
                    camera_id=camera_id,
                    session_id=session_id,
                    frame=frame,
                )
            except asyncio.CancelledError:
                raise
            except (
                    FrameRingBufferError,
                    FrameRingBufferNotFoundError,
            ):
                self._ring_buffer_error_count += 1
            except Exception:
                self._unexpected_error_count += 1
                raise
            finally:
                queue.mark_processed()

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
        SessionFrameIngestionWorkerCoordinator._consume_task_result(task)

    @staticmethod
    def _validate_queue_ownership(
            queue: SessionFrameQueue,
            camera_id: str,
            session_id: str,
    ) -> None:
        if queue.camera_id != camera_id:
            raise SessionFrameIngestionWorkerConflictError(
                f"Queue for session '{session_id}' belongs to another camera"
            )

        if queue.session_id != session_id:
            raise SessionFrameIngestionWorkerConflictError(
                f"Queue belongs to another session: '{queue.session_id}'"
            )

    @staticmethod
    def _validate_ownership(
            worker: SessionFrameIngestionWorker,
            camera_id: str,
    ) -> None:
        if worker.camera_id != camera_id:
            raise SessionFrameIngestionWorkerConflictError(
                f"Ingestion worker for session "
                f"'{worker.session_id}' belongs to another camera"
            )
