import asyncio
from datetime import datetime, timezone

import pytest

from app.domain.frame import FramePacket
from app.services.session_ai_frame_dispatch_worker import (
    SessionAIFrameDispatchWorkerCoordinator,
)
from app.services.session_frame_ingestion_worker import (
    SessionFrameIngestionWorkerConflictError,
    SessionFrameIngestionWorkerCoordinator,
)
from app.services.session_frame_queue import SessionFrameQueue
from app.services.session_frame_ring_buffer_manager import (
    FrameRingBufferNotFoundError,
)


def create_frame(
        *,
        camera_id: str,
        session_id: str,
        data: bytes = b"jpeg-data",
) -> FramePacket:
    return FramePacket(
        camera_id=camera_id,
        session_id=session_id,
        captured_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
        content_type="image/jpeg",
        data=data,
    )


class AlwaysFailingRingBufferManager:
    async def append_frame(
            self,
            camera_id: str,
            session_id: str,
            frame: FramePacket,
    ) -> None:
        raise RuntimeError("unexpected worker failure")


class NotFoundRingBufferManager:
    async def append_frame(
            self,
            camera_id: str,
            session_id: str,
            frame: FramePacket,
    ) -> None:
        raise FrameRingBufferNotFoundError(
            f"Ring buffer for session '{session_id}' was not found"
        )


class RecordingRingBufferManager:
    def __init__(self) -> None:
        self.appended_frames: list[FramePacket] = []

    async def append_frame(
            self,
            camera_id: str,
            session_id: str,
            frame: FramePacket,
    ) -> None:
        self.appended_frames.append(frame)


class FailingOnceSampler:
    def __init__(self) -> None:
        self._has_failed = False

    async def offer_frame(
            self,
            frame: FramePacket,
    ) -> None:
        if not self._has_failed:
            self._has_failed = True
            raise RuntimeError("sampler failure")

    async def clear_session(
            self,
            camera_id: str,
            session_id: str,
    ) -> bool:
        return True


class AlwaysSelectingSampler:
    def __init__(self) -> None:
        self.sampled_frame_count = 0

    async def offer_frame(
            self,
            frame: FramePacket,
    ) -> FramePacket:
        self.sampled_frame_count += 1
        return frame

    async def clear_session(
            self,
            camera_id: str,
            session_id: str,
    ) -> bool:
        return True

    async def stats(self):
        return type(
            "SamplerStats",
            (),
            {"sampled_frame_count": self.sampled_frame_count},
        )()


class RecordingAIFrameClient:
    def __init__(self) -> None:
        self.sent_frames: list[FramePacket] = []
        self._frame_sent = asyncio.Event()

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        self.sent_frames.append(frame)
        self._frame_sent.set()

    async def wait_for_sent_count(
            self,
            count: int,
    ) -> None:
        while len(self.sent_frames) < count:
            await asyncio.wait_for(self._frame_sent.wait(), timeout=1)
            self._frame_sent.clear()


class BlockingAIFrameClient:
    def __init__(self) -> None:
        self.first_send_started = asyncio.Event()
        self.release_first_send = asyncio.Event()
        self.sent_frames: list[FramePacket] = []
        self._first_send_blocked = False

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        if not self._first_send_blocked:
            self._first_send_blocked = True
            self.first_send_started.set()
            await self.release_first_send.wait()

        self.sent_frames.append(frame)


class FailingOnceAIFrameClient:
    def __init__(self) -> None:
        self.first_failure_observed = asyncio.Event()
        self.sent_frames: list[FramePacket] = []
        self._has_failed = False
        self._frame_sent = asyncio.Event()

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        if not self._has_failed:
            self._has_failed = True
            self.first_failure_observed.set()
            raise RuntimeError("ai client failure")

        self.sent_frames.append(frame)
        self._frame_sent.set()

    async def wait_for_sent_count(
            self,
            count: int,
    ) -> None:
        while len(self.sent_frames) < count:
            await asyncio.wait_for(self._frame_sent.wait(), timeout=1)
            self._frame_sent.clear()


class AlwaysTimeoutAIFrameClient:
    def __init__(self) -> None:
        self.send_started = asyncio.Event()
        self.call_count = 0

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        self.call_count += 1
        self.send_started.set()
        await asyncio.sleep(0.2)


class BlockingAndFailingRingBufferManager:
    def __init__(self) -> None:
        self.ok_append_started = asyncio.Event()
        self.release_ok_append = asyncio.Event()

    async def append_frame(
            self,
            camera_id: str,
            session_id: str,
            frame: FramePacket,
    ) -> None:
        if session_id == "session-fail":
            raise RuntimeError("unexpected worker failure")

        if session_id == "session-ok":
            self.ok_append_started.set()
            await self.release_ok_append.wait()
            return

        raise AssertionError(f"Unexpected session id: {session_id}")


@pytest.mark.asyncio
async def test_worker_continues_when_sampler_offer_frame_fails() -> None:
    sampler = FailingOnceSampler()
    coordinator = SessionFrameIngestionWorkerCoordinator(
        ai_frame_sampler=sampler,
    )
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=2,
    )
    ring_buffer_manager = RecordingRingBufferManager()

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
        queue=queue,
        ring_buffer_manager=ring_buffer_manager,
    )

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"frame-1",
        )
    )
    await asyncio.wait_for(queue.join(), timeout=1)

    assert await coordinator.active_worker_count() == 1

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"frame-2",
        )
    )
    await asyncio.wait_for(queue.join(), timeout=1)

    stats = await coordinator.stats()
    stop_result = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert stop_result is True
    assert len(ring_buffer_manager.appended_frames) == 2
    assert ring_buffer_manager.appended_frames[0].data == b"frame-1"
    assert ring_buffer_manager.appended_frames[1].data == b"frame-2"
    assert stats.ring_buffer_error_count == 0
    assert stats.unexpected_error_count == 1


@pytest.mark.asyncio
async def test_worker_forwards_sampled_frame_to_ai_client() -> None:
    ai_client = RecordingAIFrameClient()
    coordinator = SessionFrameIngestionWorkerCoordinator(
        ai_frame_sampler=AlwaysSelectingSampler(),
        ai_frame_client=ai_client,
    )
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=2,
    )
    ring_buffer_manager = RecordingRingBufferManager()

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
        queue=queue,
        ring_buffer_manager=ring_buffer_manager,
    )

    frame = create_frame(
        camera_id="camera-1",
        session_id="session-1",
        data=b"frame-1",
    )
    queue.enqueue(frame)

    await asyncio.wait_for(queue.join(), timeout=1)
    await ai_client.wait_for_sent_count(1)
    stopped = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert stopped is True
    assert len(ring_buffer_manager.appended_frames) == 1
    assert len(ai_client.sent_frames) == 1
    assert ai_client.sent_frames[0].camera_id == frame.camera_id
    assert ai_client.sent_frames[0].session_id == frame.session_id
    assert ai_client.sent_frames[0].captured_at == frame.captured_at


@pytest.mark.asyncio
async def test_worker_continues_ingestion_when_ai_client_is_slow() -> None:
    ai_client = BlockingAIFrameClient()
    coordinator = SessionFrameIngestionWorkerCoordinator(
        ai_frame_sampler=AlwaysSelectingSampler(),
        ai_frame_client=ai_client,
    )
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=3,
    )
    ring_buffer_manager = RecordingRingBufferManager()

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
        queue=queue,
        ring_buffer_manager=ring_buffer_manager,
    )

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"frame-1",
        )
    )
    await asyncio.wait_for(ai_client.first_send_started.wait(), timeout=1)

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"frame-2",
        )
    )
    await asyncio.wait_for(queue.join(), timeout=1)

    assert await coordinator.active_worker_count() == 1
    assert len(ring_buffer_manager.appended_frames) == 2

    ai_client.release_first_send.set()
    stopped = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )
    assert stopped is True


@pytest.mark.asyncio
async def test_worker_continues_when_ai_client_raises_error() -> None:
    ai_client = FailingOnceAIFrameClient()
    ai_dispatch_coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=ai_client,
        max_retries=0,
    )
    coordinator = SessionFrameIngestionWorkerCoordinator(
        ai_frame_sampler=AlwaysSelectingSampler(),
        ai_frame_dispatch_worker_coordinator=ai_dispatch_coordinator,
    )
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=2,
    )
    ring_buffer_manager = RecordingRingBufferManager()

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
        queue=queue,
        ring_buffer_manager=ring_buffer_manager,
    )

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"frame-1",
        )
    )
    await asyncio.wait_for(queue.join(), timeout=1)
    await asyncio.wait_for(ai_client.first_failure_observed.wait(), timeout=1)

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"frame-2",
        )
    )
    await asyncio.wait_for(queue.join(), timeout=1)
    await ai_client.wait_for_sent_count(1)

    stats = await coordinator.stats()
    stopped = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert stopped is True
    assert await coordinator.active_worker_count() == 0
    assert len(ring_buffer_manager.appended_frames) == 2
    assert [frame.data for frame in ai_client.sent_frames] == [b"frame-2"]
    assert stats.unexpected_error_count == 0


@pytest.mark.asyncio
async def test_start_worker_rejects_session_mismatch_queue() -> None:
    coordinator = SessionFrameIngestionWorkerCoordinator()
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-queue",
        max_frames=2,
    )

    with pytest.raises(
            SessionFrameIngestionWorkerConflictError,
            match="belongs to another session",
    ):
        await coordinator.start_worker(
            camera_id="camera-1",
            session_id="session-request",
            queue=queue,
            ring_buffer_manager=NotFoundRingBufferManager(),
        )


@pytest.mark.asyncio
async def test_worker_tracks_ring_buffer_errors_in_stats() -> None:
    coordinator = SessionFrameIngestionWorkerCoordinator()
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=2,
    )

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
        queue=queue,
        ring_buffer_manager=NotFoundRingBufferManager(),
    )

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
        )
    )
    await queue.join()

    stats = await coordinator.stats()
    stop_result = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert stats.ring_buffer_error_count == 1
    assert stats.unexpected_error_count == 0
    assert stop_result is True


@pytest.mark.asyncio
async def test_clear_continues_when_one_worker_fails_unexpectedly() -> None:
    coordinator = SessionFrameIngestionWorkerCoordinator()
    ring_buffer_manager = BlockingAndFailingRingBufferManager()

    fail_queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-fail",
        max_frames=2,
    )
    ok_queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-ok",
        max_frames=2,
    )

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-fail",
        queue=fail_queue,
        ring_buffer_manager=ring_buffer_manager,
    )
    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-ok",
        queue=ok_queue,
        ring_buffer_manager=ring_buffer_manager,
    )

    fail_queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-fail",
        )
    )
    ok_queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-ok",
        )
    )

    await fail_queue.join()
    await ring_buffer_manager.ok_append_started.wait()

    cleared_count = await coordinator.clear()
    await ok_queue.join()
    stats = await coordinator.stats()

    assert cleared_count == 2
    assert await coordinator.active_worker_count() == 0
    assert stats.unexpected_error_count >= 1


@pytest.mark.asyncio
async def test_stop_worker_consumes_task_exception_without_warning() -> None:
    loop = asyncio.get_running_loop()
    captured_contexts: list[dict[str, object]] = []
    previous_exception_handler = loop.get_exception_handler()

    def handle_exception(
            _loop,
            context,
    ) -> None:
        captured_contexts.append(context)

    loop.set_exception_handler(handle_exception)

    coordinator = SessionFrameIngestionWorkerCoordinator()
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=2,
    )

    try:
        await coordinator.start_worker(
            camera_id="camera-1",
            session_id="session-1",
            queue=queue,
            ring_buffer_manager=AlwaysFailingRingBufferManager(),
        )

        queue.enqueue(
            create_frame(
                camera_id="camera-1",
                session_id="session-1",
            )
        )
        await queue.join()

        stop_result = await coordinator.stop_worker(
            camera_id="camera-1",
            session_id="session-1",
        )
        stats = await coordinator.stats()
    finally:
        loop.set_exception_handler(previous_exception_handler)

    assert stop_result is True
    assert stats.unexpected_error_count == 1
    assert all(
        context.get("message") != "Task exception was never retrieved"
        for context in captured_contexts
    )


@pytest.mark.asyncio
async def test_clear_cleans_orphan_dispatch_workers_after_ingestion_crash() -> None:
    ai_dispatch_coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=BlockingAIFrameClient(),
    )
    coordinator = SessionFrameIngestionWorkerCoordinator(
        ai_frame_sampler=AlwaysSelectingSampler(),
        ai_frame_dispatch_worker_coordinator=ai_dispatch_coordinator,
    )
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-orphan",
        max_frames=2,
    )

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-orphan",
        queue=queue,
        ring_buffer_manager=AlwaysFailingRingBufferManager(),
    )

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-orphan",
            data=b"frame-1",
        )
    )
    await asyncio.wait_for(queue.join(), timeout=1)
    await asyncio.sleep(0.05)

    assert await coordinator.active_worker_count() == 0
    assert await ai_dispatch_coordinator.active_worker_count() == 1

    cleared_count = await coordinator.clear()
    cleared_count_again = await coordinator.clear()

    assert cleared_count == 0
    assert cleared_count_again == 0
    assert await ai_dispatch_coordinator.active_worker_count() == 0


@pytest.mark.asyncio
async def test_worker_timeout_in_ai_dispatch_does_not_stop_ingestion() -> None:
    ai_client = AlwaysTimeoutAIFrameClient()
    ai_dispatch_coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=ai_client,
        send_timeout_seconds=0.01,
        max_retries=0,
        circuit_failure_threshold=10,
        circuit_cooldown_seconds=0.1,
    )
    coordinator = SessionFrameIngestionWorkerCoordinator(
        ai_frame_sampler=AlwaysSelectingSampler(),
        ai_frame_dispatch_worker_coordinator=ai_dispatch_coordinator,
    )
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=3,
    )
    ring_buffer_manager = RecordingRingBufferManager()

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
        queue=queue,
        ring_buffer_manager=ring_buffer_manager,
    )

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"frame-1",
        )
    )
    await asyncio.wait_for(ai_client.send_started.wait(), timeout=1)

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"frame-2",
        )
    )
    await asyncio.wait_for(queue.join(), timeout=1)
    await asyncio.sleep(0.05)

    stats = await coordinator.stats()
    stopped = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert stopped is True
    assert len(ring_buffer_manager.appended_frames) == 2
    assert stats.ai_dispatch_timeout_count >= 1
    assert await coordinator.active_worker_count() == 0


@pytest.mark.asyncio
async def test_worker_stats_include_ai_sampling_and_dispatch_metrics() -> None:
    ai_client = RecordingAIFrameClient()
    sampler = AlwaysSelectingSampler()
    coordinator = SessionFrameIngestionWorkerCoordinator(
        ai_frame_sampler=sampler,
        ai_frame_client=ai_client,
    )
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=2,
    )
    ring_buffer_manager = RecordingRingBufferManager()

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
        queue=queue,
        ring_buffer_manager=ring_buffer_manager,
    )

    queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"frame-1",
        )
    )
    await asyncio.wait_for(queue.join(), timeout=1)
    await ai_client.wait_for_sent_count(1)

    stats_while_active = await coordinator.stats()
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )
    stats_after_stop = await coordinator.stats()

    assert stats_while_active.sampled_frame_count >= 1
    assert stats_while_active.ai_dispatched_frame_count >= 1
    assert stats_while_active.ai_dispatch_failure_count == 0
    assert stats_while_active.ai_dispatch_latency_avg_ms >= 0
    assert stats_while_active.active_ai_dispatch_worker_count == 1
    assert stats_while_active.ai_dispatch_available is True
    assert stats_after_stop.active_ai_dispatch_worker_count == 0