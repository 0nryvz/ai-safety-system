from datetime import datetime, timezone

import pytest

from app.domain.frame import FramePacket
from app.services.session_frame_queue import SessionFrameQueue


def create_frame(
        *,
        camera_id: str = "camera-1",
        session_id: str = "session-1",
        data: bytes = b"jpeg-data",
) -> FramePacket:
    return FramePacket(
        camera_id=camera_id,
        session_id=session_id,
        captured_at=datetime.now(timezone.utc),
        content_type="image/jpeg",
        data=data,
    )


def test_queue_requires_positive_capacity() -> None:
    with pytest.raises(
            ValueError,
            match="max_frames must be greater than zero",
    ):
        SessionFrameQueue(
            camera_id="camera-1",
            session_id="session-1",
            max_frames=0,
        )


@pytest.mark.asyncio
async def test_queue_enqueues_and_dequeues_frame() -> None:
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=2,
    )
    frame = create_frame()

    dropped_frame = queue.enqueue(frame)

    assert dropped_frame is None
    assert queue.depth == 1
    assert queue.capacity == 2

    dequeued_frame = await queue.dequeue()
    queue.mark_processed()

    assert dequeued_frame is frame
    assert queue.depth == 0


@pytest.mark.asyncio
async def test_full_queue_drops_oldest_frame() -> None:
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=2,
    )

    first_frame = create_frame(data=b"first")
    second_frame = create_frame(data=b"second")
    third_frame = create_frame(data=b"third")

    queue.enqueue(first_frame)
    queue.enqueue(second_frame)

    dropped_frame = queue.enqueue(third_frame)

    assert dropped_frame is first_frame
    assert queue.depth == 2

    first_remaining = await queue.dequeue()
    queue.mark_processed()

    second_remaining = await queue.dequeue()
    queue.mark_processed()

    assert first_remaining is second_frame
    assert second_remaining is third_frame


def test_queue_rejects_frame_from_another_session() -> None:
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=2,
    )

    foreign_frame = create_frame(
        camera_id="camera-2",
        session_id="session-2",
    )

    with pytest.raises(
            ValueError,
            match="Frame does not belong to this session queue",
    ):
        queue.enqueue(foreign_frame)

    assert queue.depth == 0


def test_clear_removes_all_waiting_frames() -> None:
    queue = SessionFrameQueue(
        camera_id="camera-1",
        session_id="session-1",
        max_frames=3,
    )

    queue.enqueue(create_frame(data=b"first"))
    queue.enqueue(create_frame(data=b"second"))

    cleared_count = queue.clear()

    assert cleared_count == 2
    assert queue.depth == 0