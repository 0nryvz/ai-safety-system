from datetime import datetime, timedelta, timezone

import pytest

from app.domain.frame import FramePacket
from app.services.session_frame_ring_buffer import (
    FrameRingBufferClosedError,
    FrameRingBufferConflictError,
    FrameRingBufferFrameTooLargeError,
    FrameRingBufferOutOfOrderError,
    FrameRingBufferValidationError,
    SessionFrameRingBuffer,
)


def create_frame(
        *,
        captured_at: datetime,
        camera_id: str = "camera-1",
        session_id: str = "session-1",
        data: bytes = b"jpeg",
) -> FramePacket:
    return FramePacket(
        camera_id=camera_id,
        session_id=session_id,
        captured_at=captured_at,
        content_type="image/jpeg",
        data=data,
    )


def test_ring_buffer_constructor_validates_limits() -> None:
    with pytest.raises(
            ValueError,
            match="buffer_seconds must be between 5 and 10",
    ):
        SessionFrameRingBuffer(
            camera_id="camera-1",
            session_id="session-1",
            buffer_seconds=4,
            max_frames=1,
            max_bytes=1,
        )

    with pytest.raises(
            ValueError,
            match="max_frames must be greater than zero",
    ):
        SessionFrameRingBuffer(
            camera_id="camera-1",
            session_id="session-1",
            buffer_seconds=5,
            max_frames=0,
            max_bytes=1,
        )

    with pytest.raises(
            ValueError,
            match="max_bytes must be greater than zero",
    ):
        SessionFrameRingBuffer(
            camera_id="camera-1",
            session_id="session-1",
            buffer_seconds=5,
            max_frames=1,
            max_bytes=0,
        )


@pytest.mark.asyncio
async def test_append_accepts_valid_frame_and_updates_stats() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    result = await ring_buffer.append(
        create_frame(captured_at=base_time, data=b"12345")
    )

    assert result.accepted is True
    assert result.frame_count == 1
    assert result.total_bytes == 5
    assert result.evicted_frame_count == 0
    assert result.evicted_bytes == 0

    stats = await ring_buffer.stats()

    assert stats.frame_count == 1
    assert stats.total_bytes == 5
    assert stats.oldest_frame_timestamp == base_time
    assert stats.newest_frame_timestamp == base_time


@pytest.mark.asyncio
async def test_append_rejects_foreign_camera_or_session() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    with pytest.raises(
            FrameRingBufferConflictError,
            match="another camera",
    ):
        await ring_buffer.append(
            create_frame(
                captured_at=base_time,
                camera_id="camera-2",
            )
        )

    with pytest.raises(
            FrameRingBufferConflictError,
            match="another session",
    ):
        await ring_buffer.append(
            create_frame(
                captured_at=base_time,
                session_id="session-2",
            )
        )


@pytest.mark.asyncio
async def test_append_rejects_timestamp_without_timezone() -> None:
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    with pytest.raises(
            FrameRingBufferValidationError,
            match="must include timezone",
    ):
        await ring_buffer.append(
            create_frame(
                captured_at=datetime(2026, 1, 1),
            )
        )


@pytest.mark.asyncio
async def test_append_normalizes_timestamp_to_utc() -> None:
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    local_time = datetime(
        2026,
        1,
        1,
        3,
        0,
        tzinfo=timezone(timedelta(hours=3)),
    )

    await ring_buffer.append(
        create_frame(captured_at=local_time)
    )

    snapshot = await ring_buffer.snapshot()

    assert snapshot[0].captured_at == datetime(
        2026,
        1,
        1,
        0,
        0,
        tzinfo=timezone.utc,
    )


@pytest.mark.asyncio
async def test_append_rejects_out_of_order_timestamp() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    await ring_buffer.append(
        create_frame(captured_at=base_time + timedelta(seconds=2))
    )

    with pytest.raises(
            FrameRingBufferOutOfOrderError,
            match="older than the newest",
    ):
        await ring_buffer.append(
            create_frame(captured_at=base_time + timedelta(seconds=1))
        )


@pytest.mark.asyncio
async def test_append_rejects_single_frame_larger_than_limit() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=3,
    )

    with pytest.raises(
            FrameRingBufferFrameTooLargeError,
            match="exceeds ring buffer max_bytes",
    ):
        await ring_buffer.append(
            create_frame(captured_at=base_time, data=b"1234")
        )


@pytest.mark.asyncio
async def test_time_window_eviction() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=5,
        max_frames=10,
        max_bytes=1024,
    )

    await ring_buffer.append(
        create_frame(captured_at=base_time, data=b"a")
    )
    await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=3),
            data=b"b",
        )
    )
    result = await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=6),
            data=b"c",
        )
    )

    assert result.frame_count == 2
    assert result.evicted_frame_count == 1
    assert result.evicted_bytes == 1

    snapshot = await ring_buffer.snapshot()

    assert tuple(frame.data for frame in snapshot) == (b"b", b"c")


@pytest.mark.asyncio
async def test_frame_count_eviction() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=2,
        max_bytes=1024,
    )

    await ring_buffer.append(
        create_frame(captured_at=base_time, data=b"first")
    )
    await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=1),
            data=b"second",
        )
    )
    result = await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=2),
            data=b"third",
        )
    )

    assert result.frame_count == 2
    assert result.evicted_frame_count == 1
    assert result.evicted_bytes == 5

    snapshot = await ring_buffer.snapshot()

    assert tuple(frame.data for frame in snapshot) == (
        b"second",
        b"third",
    )


@pytest.mark.asyncio
async def test_total_bytes_eviction() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=8,
    )

    await ring_buffer.append(
        create_frame(captured_at=base_time, data=b"aaaa")
    )
    await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=1),
            data=b"bbbb",
        )
    )
    result = await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=2),
            data=b"cccc",
        )
    )

    assert result.frame_count == 2
    assert result.total_bytes == 8
    assert result.evicted_frame_count == 1
    assert result.evicted_bytes == 4

    stats = await ring_buffer.stats()

    assert stats.camera_id == "camera-1"
    assert stats.session_id == "session-1"
    assert stats.buffer_seconds == 10
    assert stats.max_frames == 10
    assert stats.max_bytes == 8
    assert stats.oldest_frame_timestamp == (
        base_time + timedelta(seconds=1)
    )
    assert stats.newest_frame_timestamp == (
        base_time + timedelta(seconds=2)
    )
    assert stats.total_evicted_frame_count == 1
    assert stats.total_evicted_bytes == 4


@pytest.mark.asyncio
async def test_snapshot_is_chronological_immutable_and_independent() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    await ring_buffer.append(
        create_frame(captured_at=base_time, data=b"a")
    )
    await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=1),
            data=b"b",
        )
    )

    snapshot = await ring_buffer.snapshot()

    assert isinstance(snapshot, tuple)
    assert tuple(frame.data for frame in snapshot) == (b"a", b"b")

    with pytest.raises(AttributeError):
        snapshot.append(
            create_frame(
                captured_at=base_time + timedelta(seconds=2),
                data=b"c",
            )
        )

    await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=2),
            data=b"c",
        )
    )

    assert tuple(frame.data for frame in snapshot) == (b"a", b"b")
    assert tuple(frame.data for frame in await ring_buffer.snapshot()) == (
        b"a",
        b"b",
        b"c",
    )


@pytest.mark.asyncio
async def test_snapshot_does_not_clear_buffer() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    await ring_buffer.append(
        create_frame(captured_at=base_time, data=b"abc")
    )

    _ = await ring_buffer.snapshot()
    stats = await ring_buffer.stats()

    assert stats.frame_count == 1
    assert stats.total_bytes == 3


@pytest.mark.asyncio
async def test_clear_resets_buffer_and_is_idempotent() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    await ring_buffer.append(
        create_frame(captured_at=base_time, data=b"first")
    )
    await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=1),
            data=b"second",
        )
    )

    clear_result = await ring_buffer.clear()

    assert clear_result.cleared_frame_count == 2
    assert clear_result.cleared_bytes == 11

    stats = await ring_buffer.stats()

    assert stats.frame_count == 0
    assert stats.total_bytes == 0

    second_clear_result = await ring_buffer.clear()

    assert second_clear_result.cleared_frame_count == 0
    assert second_clear_result.cleared_bytes == 0


@pytest.mark.asyncio
async def test_clear_does_not_change_eviction_counters() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=1,
        max_bytes=1024,
    )

    await ring_buffer.append(
        create_frame(captured_at=base_time, data=b"first")
    )
    await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=1),
            data=b"second",
        )
    )

    before_clear_stats = await ring_buffer.stats()
    await ring_buffer.clear()
    after_clear_stats = await ring_buffer.stats()

    assert before_clear_stats.total_evicted_frame_count == 1
    assert before_clear_stats.total_evicted_bytes == 5
    assert after_clear_stats.total_evicted_frame_count == 1
    assert after_clear_stats.total_evicted_bytes == 5


@pytest.mark.asyncio
async def test_close_rejects_append_and_is_idempotent() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=1,
        max_bytes=1024,
    )

    await ring_buffer.append(
        create_frame(captured_at=base_time, data=b"first")
    )
    await ring_buffer.append(
        create_frame(
            captured_at=base_time + timedelta(seconds=1),
            data=b"second",
        )
    )

    before_close_stats = await ring_buffer.stats()
    close_result = await ring_buffer.close()
    after_close_stats = await ring_buffer.stats()

    assert close_result.cleared_frame_count == 1
    assert close_result.cleared_bytes == 6
    assert after_close_stats.frame_count == 0
    assert after_close_stats.total_bytes == 0
    assert after_close_stats.total_evicted_frame_count == (
        before_close_stats.total_evicted_frame_count
    )
    assert after_close_stats.total_evicted_bytes == (
        before_close_stats.total_evicted_bytes
    )

    with pytest.raises(
            FrameRingBufferClosedError,
            match="Ring buffer is closed",
    ):
        await ring_buffer.append(
            create_frame(
                captured_at=base_time + timedelta(seconds=2),
                data=b"third",
            )
        )

    second_close_result = await ring_buffer.close()

    assert second_close_result.cleared_frame_count == 0
    assert second_close_result.cleared_bytes == 0


@pytest.mark.asyncio
async def test_multiple_buffers_do_not_mix_sessions() -> None:
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    first_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )
    second_buffer = SessionFrameRingBuffer(
        camera_id="camera-2",
        session_id="session-2",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    await first_buffer.append(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time,
            data=b"first",
        )
    )
    await second_buffer.append(
        create_frame(
            camera_id="camera-2",
            session_id="session-2",
            captured_at=base_time,
            data=b"second",
        )
    )

    assert tuple(frame.data for frame in await first_buffer.snapshot()) == (
        b"first",
    )
    assert tuple(frame.data for frame in await second_buffer.snapshot()) == (
        b"second",
    )


@pytest.mark.asyncio
async def test_ring_buffer_does_not_create_disk_files(
        tmp_path,
        monkeypatch,
) -> None:
    monkeypatch.chdir(tmp_path)

    ring_buffer = SessionFrameRingBuffer(
        camera_id="camera-1",
        session_id="session-1",
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )

    await ring_buffer.append(
        create_frame(
            captured_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
            data=b"jpeg-bytes",
        )
    )
    await ring_buffer.snapshot()
    await ring_buffer.clear()

    assert list(tmp_path.iterdir()) == []
