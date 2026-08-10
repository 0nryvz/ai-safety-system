import asyncio
from datetime import datetime, timezone

import pytest

from app.domain.frame import FramePacket
from app.services.session_frame_ring_buffer import (
    FrameRingBufferClosedError,
)
from app.services.session_frame_ring_buffer_manager import (
    FrameRingBufferNotFoundError,
    SessionFrameRingBufferManager,
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
        captured_at=datetime(2026, 1, 1, tzinfo=timezone.utc),
        content_type="image/jpeg",
        data=data,
    )


@pytest.mark.asyncio
async def test_close_buffer_wins_against_inflight_append_without_orphan_frames(
) -> None:
    manager = SessionFrameRingBufferManager(
        buffer_seconds=10,
        max_frames=10,
        max_bytes=1024,
    )
    ring_buffer, _ = await manager.open_buffer(
        camera_id="camera-1",
        session_id="session-1",
    )

    append_started = asyncio.Event()
    allow_append = asyncio.Event()
    original_append = ring_buffer.append

    async def delayed_append(frame: FramePacket):
        append_started.set()
        await allow_append.wait()
        return await original_append(frame)

    ring_buffer.append = delayed_append  # type: ignore[method-assign]

    append_task = asyncio.create_task(
        manager.append_frame(
            camera_id="camera-1",
            session_id="session-1",
            frame=create_frame(data=b"inflight"),
        )
    )
    await append_started.wait()

    close_result = await manager.close_buffer(
        camera_id="camera-1",
        session_id="session-1",
    )
    allow_append.set()

    with pytest.raises(
            FrameRingBufferClosedError,
            match="Ring buffer is closed",
    ):
        await append_task

    assert close_result.cleared_frame_count == 0
    assert close_result.cleared_bytes == 0

    with pytest.raises(
            FrameRingBufferNotFoundError,
            match="was not found",
    ):
        await manager.get_buffer(
            camera_id="camera-1",
            session_id="session-1",
        )

    assert await ring_buffer.snapshot() == ()