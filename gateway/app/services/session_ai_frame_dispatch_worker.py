import asyncio
import time
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
    timeout_error_count: int
    retry_attempt_count: int
    latency_measurement_count: int
    total_send_latency_seconds: float
    active_worker_count: int
    ai_configured: bool
    ai_available: bool
    circuit_open: bool


@dataclass(frozen=True, slots=True)
class SessionAIFrameDispatchWorkerHealth:
    ai_configured: bool
    ai_available: bool
    circuit_open: bool


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

    async def has_frame(self) -> bool:
        async with self._lock:
            return self._pending_frame is not None


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
            send_timeout_seconds: float = 1.0,
            max_retries: int = 1,
            circuit_failure_threshold: int = 3,
            circuit_cooldown_seconds: float = 2.0,
            ai_configured: bool = True,
    ) -> None:
        if send_timeout_seconds <= 0:
            raise ValueError(
                "send_timeout_seconds must be greater than zero"
            )

        if max_retries < 0:
            raise ValueError("max_retries must be greater than or equal to zero")

        if circuit_failure_threshold <= 0:
            raise ValueError(
                "circuit_failure_threshold must be greater than zero"
            )

        if circuit_cooldown_seconds < 0:
            raise ValueError(
                "circuit_cooldown_seconds must be greater than or equal to zero"
            )

        self._workers: dict[str, SessionAIFrameDispatchWorker] = {}
        self._lock = asyncio.Lock()
        self._ai_frame_client = ai_frame_client
        self._ai_configured = ai_configured
        self._send_timeout_seconds = send_timeout_seconds
        self._max_retries = max_retries
        self._circuit_failure_threshold = circuit_failure_threshold
        self._circuit_cooldown_seconds = circuit_cooldown_seconds
        self._dispatched_frame_count = 0
        self._dropped_pending_frame_count = 0
        self._send_error_count = 0
        self._timeout_error_count = 0
        self._retry_attempt_count = 0
        self._latency_measurement_count = 0
        self._total_send_latency_seconds = 0.0
        self._consecutive_send_failure_count = 0
        self._circuit_open_until_monotonic: float | None = None

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
            circuit_open = self._is_circuit_open()
            ai_available = self._ai_configured and not circuit_open

            return SessionAIFrameDispatchWorkerStats(
                dispatched_frame_count=self._dispatched_frame_count,
                dropped_pending_frame_count=(
                    self._dropped_pending_frame_count
                ),
                send_error_count=self._send_error_count,
                timeout_error_count=self._timeout_error_count,
                retry_attempt_count=self._retry_attempt_count,
                latency_measurement_count=self._latency_measurement_count,
                total_send_latency_seconds=self._total_send_latency_seconds,
                active_worker_count=len(self._workers),
                ai_configured=self._ai_configured,
                ai_available=ai_available,
                circuit_open=circuit_open,
            )

    async def health(self) -> SessionAIFrameDispatchWorkerHealth:
        async with self._lock:
            self._remove_finished_workers()
            circuit_open = self._is_circuit_open()
            ai_available = self._ai_configured and not circuit_open

            return SessionAIFrameDispatchWorkerHealth(
                ai_configured=self._ai_configured,
                ai_available=ai_available,
                circuit_open=circuit_open,
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

            if not self._ai_configured:
                continue

            if self._is_circuit_open():
                self._send_error_count += 1
                continue

            try:
                sent = await self._send_frame_with_resilience(
                    frame=frame,
                    pending_slot=pending_slot,
                )

                if sent:
                    self._dispatched_frame_count += 1
                    self._consecutive_send_failure_count = 0
                    self._circuit_open_until_monotonic = None
                else:
                    self._register_send_failure()
            except asyncio.CancelledError:
                raise

    async def _send_frame_with_resilience(
            self,
            frame: FramePacket,
            pending_slot: SessionAIFramePendingSlot,
    ) -> bool:
        max_attempts = self._max_retries + 1

        for attempt_index in range(max_attempts):
            started_at = time.monotonic()

            try:
                await asyncio.wait_for(
                    self._ai_frame_client.send_frame(frame),
                    timeout=self._send_timeout_seconds,
                )
                elapsed = time.monotonic() - started_at
                self._total_send_latency_seconds += elapsed
                self._latency_measurement_count += 1
                return True
            except asyncio.CancelledError:
                raise
            except TimeoutError:
                self._timeout_error_count += 1
            except Exception:
                pass

            if attempt_index >= (max_attempts - 1):
                break

            if await pending_slot.has_frame():
                break

            self._retry_attempt_count += 1

        return False

    def _register_send_failure(self) -> None:
        self._send_error_count += 1
        self._consecutive_send_failure_count += 1

        if (
                self._consecutive_send_failure_count
                >= self._circuit_failure_threshold
        ):
            self._circuit_open_until_monotonic = (
                time.monotonic() + self._circuit_cooldown_seconds
            )

    def _is_circuit_open(self) -> bool:
        open_until = self._circuit_open_until_monotonic

        if open_until is None:
            return False

        if time.monotonic() >= open_until:
            self._circuit_open_until_monotonic = None
            return False

        return True

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
