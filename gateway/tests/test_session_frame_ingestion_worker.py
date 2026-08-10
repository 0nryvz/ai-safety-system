import asyncio
from datetime import datetime, timezone

import pytest

from app.domain.frame import FramePacket
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
    async def offer_frame(
            self,
            frame: FramePacket,
    ) -> FramePacket:
        return frame

    async def clear_session(
            self,
            camera_id: str,
            session_id: str,
    ) -> bool:
        return True


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