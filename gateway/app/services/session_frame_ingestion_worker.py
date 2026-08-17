import asyncio
from contextlib import suppress
from dataclasses import dataclass

from app.domain.frame import FramePacket
from app.services.ai_frame_client import AIFrameClient, NoOpAIFrameClient
from app.services.session_ai_frame_dispatch_worker import (
    SessionAIFrameDispatchWorkerConflictError,
    SessionAIFrameDispatchWorkerCoordinator,
)
from app.services.session_ai_frame_sampler import (
    SessionAIFrameSampler,
    SessionAIFrameSamplerStats,
)
from app.services.session_frame_queue import SessionFrameQueue
from app.services.session_frame_ring_buffer import FrameRingBufferError
from app.services.session_frame_ring_buffer_manager import (
    FrameRingBufferNotFoundError,
    SessionFrameRingBufferManager,
)
from app.services.event_recorder import (
    EventRecorderCoordinator,
)

class SessionFrameIngestionWorkerConflictError(RuntimeError):
    """Worker session identity belongs to another camera."""


@dataclass(frozen=True, slots=True)
class SessionFrameIngestionWorkerStats:
    ring_buffer_error_count: int
    unexpected_error_count: int
    sampled_frame_count: int
    ai_dispatched_frame_count: int
    ai_dropped_stale_frame_count: int
    ai_dispatch_failure_count: int
    ai_dispatch_timeout_count: int
    ai_dispatch_retry_count: int
    ai_dispatch_latency_avg_ms: float
    active_ai_dispatch_worker_count: int
    ai_dispatch_configured: bool
    ai_dispatch_available: bool
    ai_dispatch_circuit_open: bool


@dataclass(frozen=True, slots=True)
class SessionFrameIngestionWorker:
    camera_id: str
    session_id: str
    task: asyncio.Task[None]


class SessionFrameIngestionWorkerCoordinator:
    def __init__(
            self,
            ai_frame_sampler: SessionAIFrameSampler | None = None,
            ai_frame_dispatch_worker_coordinator: (
                    SessionAIFrameDispatchWorkerCoordinator | None
            ) = None,
            ai_frame_client: AIFrameClient | None = None,
            event_recorder_coordinator: (
                    EventRecorderCoordinator | None
            ) = None,
    ) -> None:
        self._event_recorder_coordinator = (
            event_recorder_coordinator
        )
        self._workers: dict[str, SessionFrameIngestionWorker] = {}
        self._lock = asyncio.Lock()
        self._ring_buffer_error_count = 0
        self._unexpected_error_count = 0
        self._ai_frame_sampler = (
            ai_frame_sampler
            if ai_frame_sampler is not None
            else SessionAIFrameSampler()
        )
        self._ai_frame_dispatch_worker_coordinator = (
            ai_frame_dispatch_worker_coordinator
            if ai_frame_dispatch_worker_coordinator is not None
            else SessionAIFrameDispatchWorkerCoordinator(
                ai_frame_client=(
                    ai_frame_client
                    if ai_frame_client is not None
                    else NoOpAIFrameClient()
                ),
                ai_configured=(ai_frame_client is not None),
            )
        )

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

            try:
                await self._ai_frame_dispatch_worker_coordinator.start_worker(
                    camera_id=camera_id,
                    session_id=session_id,
                )
            except SessionAIFrameDispatchWorkerConflictError as exc:
                raise SessionFrameIngestionWorkerConflictError(
                    f"AI dispatch worker for session "
                    f"'{session_id}' belongs to another camera"
                ) from exc

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
        worker: SessionFrameIngestionWorker | None = None

        async with self._lock:
            existing_worker = self._workers.get(session_id)

            if existing_worker is not None:
                self._validate_ownership(
                    worker=existing_worker,
                    camera_id=camera_id,
                )

                worker = self._workers.pop(session_id)

        ingestion_stopped = False

        if worker is not None:
            await self._stop_task(worker.task)
            ingestion_stopped = True

        dispatch_stopped = (
            await self._ai_frame_dispatch_worker_coordinator.stop_worker(
                camera_id=camera_id,
                session_id=session_id,
            )
        )

        sampler_cleared = await self._ai_frame_sampler.clear_session(
            camera_id=camera_id,
            session_id=session_id,
        )

        return (
                ingestion_stopped
                or dispatch_stopped
                or sampler_cleared
        )

    async def active_worker_count(self) -> int:
        async with self._lock:
            self._remove_finished_workers()
            return len(self._workers)

    async def stats(
            self,
    ) -> SessionFrameIngestionWorkerStats:
        async with self._lock:
            self._remove_finished_workers()
            ring_buffer_error_count = self._ring_buffer_error_count
            unexpected_error_count = self._unexpected_error_count

        sampler_stats = await self._get_sampler_stats()
        dispatch_stats = (
            await self._ai_frame_dispatch_worker_coordinator.stats()
        )
        ai_dispatch_latency_avg_ms = 0.0

        if dispatch_stats.latency_measurement_count > 0:
            ai_dispatch_latency_avg_ms = (
                dispatch_stats.total_send_latency_seconds
                * 1000
                / dispatch_stats.latency_measurement_count
            )

        return SessionFrameIngestionWorkerStats(
            ring_buffer_error_count=ring_buffer_error_count,
            unexpected_error_count=unexpected_error_count,
            sampled_frame_count=sampler_stats.sampled_frame_count,
            ai_dispatched_frame_count=(
                dispatch_stats.dispatched_frame_count
            ),
            ai_dropped_stale_frame_count=(
                dispatch_stats.dropped_pending_frame_count
            ),
            ai_dispatch_failure_count=dispatch_stats.send_error_count,
            ai_dispatch_timeout_count=(
                dispatch_stats.timeout_error_count
            ),
            ai_dispatch_retry_count=dispatch_stats.retry_attempt_count,
            ai_dispatch_latency_avg_ms=ai_dispatch_latency_avg_ms,
            active_ai_dispatch_worker_count=(
                dispatch_stats.active_worker_count
            ),
            ai_dispatch_configured=dispatch_stats.ai_configured,
            ai_dispatch_available=dispatch_stats.ai_available,
            ai_dispatch_circuit_open=dispatch_stats.circuit_open,
        )

    async def clear(self) -> int:
        async with self._lock:
            workers = tuple(self._workers.values())
            self._workers.clear()

        for worker in workers:
            with suppress(Exception):
                await self._stop_task(worker.task)

            with suppress(Exception):
                await self._ai_frame_dispatch_worker_coordinator.stop_worker(
                    camera_id=worker.camera_id,
                    session_id=worker.session_id,
                )

            with suppress(Exception):
                await self._ai_frame_sampler.clear_session(
                    camera_id=worker.camera_id,
                    session_id=worker.session_id,
                )

        # Ingestion registry'den daha önce düşmüş orphan dispatch
        # worker'ları da garanti olarak temizle.
        with suppress(Exception):
            await self._ai_frame_dispatch_worker_coordinator.clear()

        # Ingestion worker daha önce öldüyse geride kalmış sampler
        # state'lerini de temizle.
        sampler_clear = getattr(
            self._ai_frame_sampler,
            "clear",
            None,
        )

        if sampler_clear is not None:
            with suppress(Exception):
                await sampler_clear()

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

                if self._event_recorder_coordinator is not None:
                    try:
                        await (
                            self
                            ._event_recorder_coordinator
                            .offer_frame(
                                frame
                            )
                        )

                    except asyncio.CancelledError:
                        raise

                    except Exception:
                        self._unexpected_error_count += 1

                sampled_frame: FramePacket | None = None

                try:
                    sampled_frame = await self._ai_frame_sampler.offer_frame(
                        frame=frame,
                    )
                except asyncio.CancelledError:
                    raise
                except Exception:
                    self._unexpected_error_count += 1

                if sampled_frame is not None:
                    try:
                        await self._ai_frame_dispatch_worker_coordinator.offer_frame(
                            frame=sampled_frame,
                        )
                    except asyncio.CancelledError:
                        raise
                    except Exception:
                        self._unexpected_error_count += 1
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

    async def _get_sampler_stats(self) -> SessionAIFrameSamplerStats:
        stats_getter = getattr(self._ai_frame_sampler, "stats", None)

        if stats_getter is None:
            return SessionAIFrameSamplerStats(sampled_frame_count=0)

        try:
            sampler_stats = await stats_getter()
        except Exception:
            return SessionAIFrameSamplerStats(sampled_frame_count=0)

        sampled_frame_count = getattr(
            sampler_stats,
            "sampled_frame_count",
            0,
        )

        if not isinstance(sampled_frame_count, int):
            return SessionAIFrameSamplerStats(sampled_frame_count=0)

        return SessionAIFrameSamplerStats(
            sampled_frame_count=sampled_frame_count,
        )

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
