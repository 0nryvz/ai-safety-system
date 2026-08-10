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


class AlwaysFailingAIFrameClient:
    def __init__(self) -> None:
        self.call_count = 0
        self._called = asyncio.Event()

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        self.call_count += 1
        self._called.set()
        raise RuntimeError("always fail")

    async def wait_for_call_count(
            self,
            count: int,
    ) -> None:
        while self.call_count < count:
            await asyncio.wait_for(self._called.wait(), timeout=1)
            self._called.clear()


class TimeoutThenSuccessAIFrameClient:
    def __init__(self) -> None:
        self.call_count = 0
        self.sent_frames: list[FramePacket] = []
        self._frame_sent = asyncio.Event()

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        self.call_count += 1

        if self.call_count == 1:
            await asyncio.sleep(0.05)
            return

        self.sent_frames.append(frame)
        self._frame_sent.set()

    async def wait_for_sent_count(
            self,
            count: int,
    ) -> None:
        while len(self.sent_frames) < count:
            await asyncio.wait_for(self._frame_sent.wait(), timeout=1)
            self._frame_sent.clear()


class ScriptedAIFrameClient:
    def __init__(
            self,
            outcomes: list[str],
    ) -> None:
        self._outcomes = list(outcomes)
        self.call_count = 0
        self.sent_frames: list[FramePacket] = []
        self._called = asyncio.Event()
        self._frame_sent = asyncio.Event()

    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        self.call_count += 1
        self._called.set()

        outcome = "success"
        if self._outcomes:
            outcome = self._outcomes.pop(0)

        if outcome == "fail":
            raise RuntimeError("scripted fail")

        self.sent_frames.append(frame)
        self._frame_sent.set()

    async def wait_for_call_count(
            self,
            count: int,
    ) -> None:
        while self.call_count < count:
            await asyncio.wait_for(self._called.wait(), timeout=1)
            self._called.clear()

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
        max_retries=0,
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


@pytest.mark.asyncio
async def test_dispatch_worker_retries_after_timeout_with_bound_limit() -> None:
    client = TimeoutThenSuccessAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
        send_timeout_seconds=0.01,
        max_retries=1,
        circuit_failure_threshold=5,
        circuit_cooldown_seconds=0.1,
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

    await client.wait_for_sent_count(1)
    stats = await coordinator.stats()
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert client.call_count == 2
    assert stats.timeout_error_count == 1
    assert stats.retry_attempt_count == 1
    assert stats.dispatched_frame_count == 1
    assert stats.send_error_count == 0
    assert stats.latency_measurement_count == 1
    assert stats.total_send_latency_seconds > 0


@pytest.mark.asyncio
async def test_dispatch_worker_keeps_retry_bounded_when_client_always_fails() -> None:
    client = AlwaysFailingAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
        send_timeout_seconds=0.01,
        max_retries=2,
        circuit_failure_threshold=10,
        circuit_cooldown_seconds=0.1,
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

    await client.wait_for_call_count(3)
    stats = await coordinator.stats()
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert client.call_count == 3
    assert stats.retry_attempt_count == 2
    assert stats.send_error_count >= 1


@pytest.mark.asyncio
async def test_dispatch_worker_opens_circuit_and_reports_unavailable() -> None:
    client = AlwaysFailingAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
        send_timeout_seconds=0.01,
        max_retries=0,
        circuit_failure_threshold=1,
        circuit_cooldown_seconds=5,
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
    await client.wait_for_call_count(1)

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=333),
            data=b"frame-2",
        )
    )
    await asyncio.sleep(0.05)

    stats = await coordinator.stats()
    health = await coordinator.health()
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert client.call_count == 1
    assert stats.circuit_open is True
    assert stats.ai_available is False
    assert health.ai_available is False
    assert health.circuit_open is True
    assert stats.send_error_count >= 2


@pytest.mark.asyncio
async def test_dispatch_worker_opens_circuit_when_failure_threshold_reached() -> None:
    client = AlwaysFailingAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
        send_timeout_seconds=0.01,
        max_retries=0,
        circuit_failure_threshold=2,
        circuit_cooldown_seconds=5,
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
    await client.wait_for_call_count(1)
    stats_after_first_failure = await coordinator.stats()

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=333),
            data=b"frame-2",
        )
    )
    await client.wait_for_call_count(2)
    stats_after_second_failure = await coordinator.stats()
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert stats_after_first_failure.circuit_open is False
    assert stats_after_second_failure.circuit_open is True
    assert stats_after_second_failure.ai_available is False


@pytest.mark.asyncio
async def test_dispatch_worker_does_not_send_before_cooldown_but_probes_after() -> None:
    client = AlwaysFailingAIFrameClient()
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
        send_timeout_seconds=0.01,
        max_retries=0,
        circuit_failure_threshold=1,
        circuit_cooldown_seconds=0.2,
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
    await client.wait_for_call_count(1)

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=333),
            data=b"frame-2",
        )
    )
    await asyncio.sleep(0.05)
    stats_during_cooldown = await coordinator.stats()

    await asyncio.sleep(0.2)
    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=666),
            data=b"frame-3",
        )
    )
    await client.wait_for_call_count(2)
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert client.call_count == 2
    assert stats_during_cooldown.circuit_open is True


@pytest.mark.asyncio
async def test_dispatch_worker_resets_failure_state_after_success() -> None:
    client = ScriptedAIFrameClient(
        outcomes=["fail", "fail", "success", "fail", "success"],
    )
    coordinator = SessionAIFrameDispatchWorkerCoordinator(
        ai_frame_client=client,
        send_timeout_seconds=0.01,
        max_retries=0,
        circuit_failure_threshold=2,
        circuit_cooldown_seconds=0.05,
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
    await client.wait_for_call_count(1)

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=333),
            data=b"frame-2",
        )
    )
    await client.wait_for_call_count(2)
    stats_after_open = await coordinator.stats()

    await asyncio.sleep(0.06)
    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=666),
            data=b"frame-3",
        )
    )
    await client.wait_for_sent_count(1)
    stats_after_success = await coordinator.stats()

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=999),
            data=b"frame-4",
        )
    )
    await client.wait_for_call_count(4)
    stats_after_single_failure = await coordinator.stats()

    await coordinator.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=1332),
            data=b"frame-5",
        )
    )
    await client.wait_for_sent_count(2)
    final_stats = await coordinator.stats()
    await coordinator.stop_worker(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert stats_after_open.circuit_open is True
    assert stats_after_success.circuit_open is False
    assert stats_after_success.ai_available is True
    assert stats_after_single_failure.circuit_open is False
    assert final_stats.dispatched_frame_count == 2
