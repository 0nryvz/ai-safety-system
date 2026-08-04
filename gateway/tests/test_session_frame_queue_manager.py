from datetime import datetime, timezone

import pytest

from app.domain.frame import FramePacket
from app.services.session_frame_queue_manager import (
    FrameQueueConflictError,
    FrameQueueNotFoundError,
    SessionFrameQueueManager,
)


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


def test_queue_manager_requires_positive_capacity() -> None:
    with pytest.raises(
            ValueError,
            match="max_frames must be greater than zero",
    ):
        SessionFrameQueueManager(max_frames=0)


@pytest.mark.asyncio
async def test_open_queue_creates_session_queue() -> None:
    manager = SessionFrameQueueManager(max_frames=3)

    queue, created = await manager.open_queue(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert created is True
    assert queue.camera_id == "camera-1"
    assert queue.session_id == "session-1"
    assert queue.capacity == 3
    assert await manager.active_queue_count() == 1


@pytest.mark.asyncio
async def test_open_queue_reuses_existing_queue_on_reconnect() -> None:
    manager = SessionFrameQueueManager(max_frames=3)

    first_queue, first_created = await manager.open_queue(
        camera_id="camera-1",
        session_id="session-1",
    )

    second_queue, second_created = await manager.open_queue(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert first_created is True
    assert second_created is False
    assert second_queue is first_queue
    assert await manager.active_queue_count() == 1


@pytest.mark.asyncio
async def test_same_session_queue_cannot_belong_to_another_camera() -> None:
    manager = SessionFrameQueueManager(max_frames=3)

    await manager.open_queue(
        camera_id="camera-1",
        session_id="session-1",
    )

    with pytest.raises(
            FrameQueueConflictError,
            match="belongs to another camera",
    ):
        await manager.open_queue(
            camera_id="camera-2",
            session_id="session-1",
        )


@pytest.mark.asyncio
async def test_get_queue_rejects_missing_session() -> None:
    manager = SessionFrameQueueManager(max_frames=3)

    with pytest.raises(
            FrameQueueNotFoundError,
            match="was not found",
    ):
        await manager.get_queue(
            camera_id="camera-1",
            session_id="missing-session",
        )


@pytest.mark.asyncio
async def test_close_queue_clears_waiting_frames() -> None:
    manager = SessionFrameQueueManager(max_frames=3)

    queue, _ = await manager.open_queue(
        camera_id="camera-1",
        session_id="session-1",
    )

    queue.enqueue(create_frame(data=b"first"))
    queue.enqueue(create_frame(data=b"second"))

    cleared_frame_count = await manager.close_queue(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert cleared_frame_count == 2
    assert queue.depth == 0
    assert await manager.active_queue_count() == 0

    second_close_result = await manager.close_queue(
        camera_id="camera-1",
        session_id="session-1",
    )

    assert second_close_result == 0


@pytest.mark.asyncio
async def test_clear_removes_all_session_queues() -> None:
    manager = SessionFrameQueueManager(max_frames=3)

    first_queue, _ = await manager.open_queue(
        camera_id="camera-1",
        session_id="session-1",
    )

    second_queue, _ = await manager.open_queue(
        camera_id="camera-2",
        session_id="session-2",
    )

    first_queue.enqueue(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            data=b"first",
        )
    )
    second_queue.enqueue(
        create_frame(
            camera_id="camera-2",
            session_id="session-2",
            data=b"second",
        )
    )

    cleared_frame_count = await manager.clear()

    assert cleared_frame_count == 2
    assert first_queue.depth == 0
    assert second_queue.depth == 0
    assert await manager.active_queue_count() == 0