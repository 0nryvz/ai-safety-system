import asyncio
import logging
from datetime import datetime, timedelta

from app.domain.session import CameraSessionContext, utc_now
from app.services.event_recorder import EventRecorderCoordinator
from app.services.session_frame_ingestion_worker import (
    SessionFrameIngestionWorkerCoordinator,
)
from app.services.session_frame_queue_manager import SessionFrameQueueManager
from app.services.session_frame_ring_buffer_manager import (
    SessionFrameRingBufferManager,
)
from app.services.session_lifecycle_notifier import (
    CameraSessionLifecycleNotifier,
)
from app.services.session_manager import SessionManager


logger = logging.getLogger(__name__)


class SessionStaleCleanupCoordinator:
    def __init__(
            self,
            *,
            session_manager: SessionManager,
            session_frame_queue_manager: SessionFrameQueueManager,
            session_frame_ring_buffer_manager: SessionFrameRingBufferManager,
            ingestion_worker_coordinator: SessionFrameIngestionWorkerCoordinator,
            event_recorder_coordinator: EventRecorderCoordinator,
            session_lifecycle_notifier: CameraSessionLifecycleNotifier,
            stale_timeout_seconds: float,
            sweep_interval_seconds: float,
    ) -> None:
        if stale_timeout_seconds <= 0:
            raise ValueError(
                "stale_timeout_seconds must be greater than zero"
            )

        if sweep_interval_seconds <= 0:
            raise ValueError(
                "sweep_interval_seconds must be greater than zero"
            )

        self._session_manager = session_manager
        self._session_frame_queue_manager = session_frame_queue_manager
        self._session_frame_ring_buffer_manager = (
            session_frame_ring_buffer_manager
        )
        self._ingestion_worker_coordinator = ingestion_worker_coordinator
        self._event_recorder_coordinator = event_recorder_coordinator
        self._session_lifecycle_notifier = session_lifecycle_notifier

        self._stale_timeout = timedelta(
            seconds=stale_timeout_seconds
        )
        self._sweep_interval_seconds = sweep_interval_seconds

        self._task: asyncio.Task[None] | None = None
        self._lock = asyncio.Lock()

    async def start(self) -> bool:
        async with self._lock:
            if self._task is not None and not self._task.done():
                return False

            self._task = asyncio.create_task(self._run_loop())
            return True

    async def stop(self) -> bool:
        async with self._lock:
            task = self._task
            self._task = None

        if task is None:
            return False

        if not task.done():
            task.cancel()

        try:
            await task
        except asyncio.CancelledError:
            pass

        return True

    async def run_once(
            self,
            *,
            now: datetime | None = None,
    ) -> int:
        current_time = now or utc_now()
        stale_before = current_time - self._stale_timeout

        stale_sessions = (
            await self._session_manager.claim_stale_sessions(
                stale_before=stale_before,
            )
        )

        for session in stale_sessions:
            await self._cleanup_claimed_session(session)

        return len(stale_sessions)

    async def _run_loop(self) -> None:
        while True:
            try:
                await self.run_once()
            except asyncio.CancelledError:
                raise
            except Exception:
                logger.exception("Stale session sweep failed")

            await asyncio.sleep(self._sweep_interval_seconds)

    async def _cleanup_claimed_session(
            self,
            session: CameraSessionContext,
    ) -> None:
        camera_id = session.camera_id
        session_id = session.session_id

        await self._best_effort(
            "stop ingestion worker",
            self._ingestion_worker_coordinator.stop_worker(
                camera_id=camera_id,
                session_id=session_id,
            ),
        )

        await self._best_effort(
            "finalize recorder session",
            self._event_recorder_coordinator.finalize_session(
                camera_id=camera_id,
                session_id=session_id,
            ),
        )

        await self._best_effort(
            "close frame queue",
            self._session_frame_queue_manager.close_queue(
                camera_id=camera_id,
                session_id=session_id,
            ),
        )

        await self._best_effort(
            "close ring buffer",
            self._session_frame_ring_buffer_manager.close_buffer(
                camera_id=camera_id,
                session_id=session_id,
            ),
        )

        if session.closed_at is not None:
            await self._best_effort(
                "notify session close",
                self._session_lifecycle_notifier.notify_close(
                    camera_id=camera_id,
                    session_id=session_id,
                    closed_at=session.closed_at,
                ),
            )

    async def _best_effort(
            self,
            action: str,
            awaitable,
    ) -> None:
        try:
            await awaitable
        except asyncio.CancelledError:
            raise
        except Exception:
            logger.exception(
                "Stale session cleanup action failed: %s",
                action,
            )