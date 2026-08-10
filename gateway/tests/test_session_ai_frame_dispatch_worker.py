import asyncio
from datetime import datetime, timedelta, timezone

import pytest

from app.domain.frame import FramePacket
from app.services.session_ai_frame_dispatch_worker import (
    SessionAIFrameDispatchWorkerCoordinator,
)


def create_frame(
        *,
        camera_id: str,
        session_id: str,
        captured_at: datetime,
        data: bytes,
) -> FramePacket:
    return FramePacket(
        camera_id=camera_id,
        session_id=session_id,
        captured_at=captured_at,
        content_type="image/jpeg",
        data=data,
    )


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
        self.sent_frames: list[FramePacket] = []
        self.first_send_started = asyncio.Event()
        self.release_first_send = asyncio.Event()
        self._first_send_blocked = False
        self._frame_sent = asyncio.Event()

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        if not self._first_send_blocked:
            self._first_send_blocked = True
            self.first_send_started.set()
            await self.release_first_send.wait()

        self.sent_frames.append(frame)
        self._frame_sent.set()

    async def wait_for_sent_count(
            self,
            count: int,
    ) -> None:
        while len(self.sent_frames) < count:
            await asyncio.wait_for(self._frame_sent.wait(), timeout=1)
            self._frame_sent.clear()


class FailingOnceAIFrameClient:
    def __init__(self) -> None:
        self.sent_frames: list[FramePacket] = []
        self.first_failure_observed = asyncio.Event()
        self._has_failed = False
        self._frame_sent = asyncio.Event()

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        if not self._has_failed:
            self._has_failed = True
            self.first_failure_observed.set()
            raise RuntimeError("ai send failure")

        self.sent_frames.append(frame)
        self._frame_sent.set()

    async def wait_for_sent_count(
            self,
            count: int,
    ) -> None:
        while len(self.sent_frames) < count:
            await asyncio.wait_for(self._frame_sent.wait(), timeout=1)
            self._frame_sent.clear()


@pytest.mark.asyncio
async def test_dispatch_worker_sends_frame_to_ai_client() -> None:
    client = RecordingAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
    )
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    frame = create_frame(
        camera_id="camera-1",
        session_id="session-1",
        captured_at=base_time,
        data=b"frame-1",
    )

    started = await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
    )
    offered = await coordinator.offer_frame(frame)
    await client.wait_for_sent_count(1)
    stopped = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert started is True
    assert offered is True
    assert stopped is True
    assert len(client.sent_frames) == 1
    assert client.sent_frames[0].camera_id == "camera-1"
    assert client.sent_frames[0].session_id == "session-1"
    assert client.sent_frames[0].captured_at == base_time


@pytest.mark.asyncio
async def test_dispatch_worker_prefers_latest_pending_frame_when_ai_is_slow() -> None:
    client = BlockingAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
    )
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time,
            data=b"frame-1",
        )
    )
    await asyncio.wait_for(client.first_send_started.wait(), timeout=1)

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=333),
            data=b"frame-2",
        )
    )
    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=666),
            data=b"frame-3",
        )
    )

    client.release_first_send.set()
    await client.wait_for_sent_count(2)
    stats = await coordinator.stats()
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert [frame.data for frame in client.sent_frames] == [
        b"frame-1",
        b"frame-3",
    ]
    assert stats.dropped_pending_frame_count == 1


@pytest.mark.asyncio
async def test_dispatch_worker_keeps_state_isolated_between_sessions() -> None:
    client = RecordingAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
    )
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-a",
    )
    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-b",
    )

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-a",
            captured_at=base_time,
            data=b"a-1",
        )
    )
    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-b",
            captured_at=base_time,
            data=b"b-1",
        )
    )

    await client.wait_for_sent_count(2)
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-a",
    )
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-b",
    )

    assert {frame.session_id for frame in client.sent_frames} == {
        "session-a",
        "session-b",
    }


@pytest.mark.asyncio
async def test_dispatch_worker_reconnect_does_not_create_second_worker() -> None:
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=RecordingAIFrameClient(),
    )

    first_start = await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
    )
    second_start = await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
    )
    worker_count = await coordinator.active_worker_count()
    stopped = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert first_start is True
    assert second_start is False
    assert worker_count == 1
    assert stopped is True


@pytest.mark.asyncio
async def test_dispatch_worker_continues_when_ai_client_fails() -> None:
    client = FailingOnceAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
    )
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time,
            data=b"frame-1",
        )
    )
    await asyncio.wait_for(client.first_failure_observed.wait(), timeout=1)

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=400),
            data=b"frame-2",
        )
    )

    await client.wait_for_sent_count(1)
    stats = await coordinator.stats()
    stopped = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert stopped is True
    assert [frame.data for frame in client.sent_frames] == [b"frame-2"]
    assert stats.send_error_count == 1
    assert stats.dispatched_frame_count == 1


@pytest.mark.asyncio
async def test_dispatch_worker_stop_cleans_pending_frame_and_is_idempotent() -> None:
    client = BlockingAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
    )
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)

    await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time,
            data=b"frame-1",
        )
    )
    await asyncio.wait_for(client.first_send_started.wait(), timeout=1)

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=333),
            data=b"frame-2",
        )
    )

    first_stop = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )
    second_stop = await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    client.release_first_send.set()

    restarted = await coordinator.start_worker(
        camera_id="camera-1",
        session_id="session-1",
    )
    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=666),
            data=b"frame-3",
        )
    )
    await client.wait_for_sent_count(1)
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert first_stop is True
    assert second_stop is False
    assert restarted is True
    assert [frame.data for frame in client.sent_frames] == [b"frame-3"]
