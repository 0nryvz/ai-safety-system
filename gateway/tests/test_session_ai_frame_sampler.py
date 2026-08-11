from datetime import datetime, timedelta, timezone

import pytest

from app.domain.frame import FramePacket
from app.services.session_ai_frame_sampler import (
    SessionAIFrameSampler,
    SessionAIFrameSamplerConflictError,
)


def create_frame(
        *,
        camera_id: str,
        session_id: str,
        captured_at: datetime,
        data: bytes = b"jpeg-data",
) -> FramePacket:
    return FramePacket(
        camera_id=camera_id,
        session_id=session_id,
        captured_at=captured_at,
        content_type="image/jpeg",
        data=data,
    )


@pytest.mark.asyncio
async def test_sampler_selects_approximately_three_fps_from_15_fps() -> None:
    sampler = SessionAIFrameSampler()
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    frame_interval = timedelta(microseconds=66_667)
    selected_frames: list[FramePacket] = []

    for frame_index in range(15):
        frame = create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + (frame_interval * frame_index),
        )

        selected_frame = await sampler.offer_frame(frame)
        if selected_frame is not None:
            selected_frames.append(selected_frame)

    assert len(selected_frames) == 3
    assert selected_frames[0].camera_id == "camera-1"
    assert selected_frames[0].session_id == "session-1"
    cadence_seconds = [
        (
            selected_frames[index].captured_at
            - selected_frames[index - 1].captured_at
        ).total_seconds()
        for index in range(1, len(selected_frames))
    ]
    assert cadence_seconds == pytest.approx([1 / 3, 1 / 3], abs=0.002)


@pytest.mark.asyncio
async def test_sampler_uses_timestamp_based_selection_not_frame_count() -> None:
    sampler = SessionAIFrameSampler()
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)
    selected_indices: list[int] = []

    offsets_ms = [0, 50, 100, 150, 200, 333]

    for index, offset_ms in enumerate(offsets_ms):
        frame = create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=offset_ms),
        )

        if await sampler.offer_frame(frame) is not None:
            selected_indices.append(index)

    assert selected_indices == [0, 5]


@pytest.mark.asyncio
async def test_sampler_does_not_select_new_frame_before_333ms() -> None:
    sampler = SessionAIFrameSampler()
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)

    first = await sampler.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time,
        )
    )
    before_interval = await sampler.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=332),
        )
    )
    at_interval = await sampler.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time + timedelta(milliseconds=333),
        )
    )

    assert first is not None
    assert before_interval is None
    assert at_interval is not None


@pytest.mark.asyncio
async def test_sampler_keeps_sampling_state_isolated_between_sessions() -> None:
    sampler = SessionAIFrameSampler()
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)

    session_a_first = await sampler.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-a",
            captured_at=base_time,
        )
    )
    session_a_second = await sampler.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-a",
            captured_at=base_time + timedelta(milliseconds=100),
        )
    )
    session_b_first = await sampler.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-b",
            captured_at=base_time + timedelta(milliseconds=100),
        )
    )

    assert session_a_first is not None
    assert session_a_second is None
    assert session_b_first is not None


@pytest.mark.asyncio
async def test_sampler_raises_conflict_when_session_camera_mismatches() -> None:
    sampler = SessionAIFrameSampler()
    base_time = datetime(2026, 1, 1, tzinfo=timezone.utc)

    first = await sampler.offer_frame(
        create_frame(
            camera_id="camera-1",
            session_id="session-1",
            captured_at=base_time,
        )
    )

    with pytest.raises(
            SessionAIFrameSamplerConflictError,
            match="belongs to another camera",
    ):
        await sampler.offer_frame(
            create_frame(
                camera_id="camera-2",
                session_id="session-1",
                captured_at=base_time + timedelta(milliseconds=400),
            )
        )

    assert first is not None