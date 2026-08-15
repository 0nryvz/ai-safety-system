import base64
from datetime import (
    datetime,
    timedelta,
    timezone,
)
from pathlib import Path

import pytest

from app.domain.frame import FramePacket
from app.infrastructure.ffmpeg_video_encoder import (
    FfmpegVideoEncoder,
)
from app.services.event_recorder import (
    EventRecorderCoordinator,
    EventRecordingStatus,
)
from app.services.session_frame_ring_buffer_manager import (
    SessionFrameRingBufferManager,
)


TEST_JPEG = base64.b64decode(
    "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQ"
    "FxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoICh"
    "MKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKC"
    "goKCgoKCgoKCj/wAARCAACAAIDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAA"
    "AAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhBy"
    "JxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpT"
    "VFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqr"
    "KztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8Q"
    "AHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAA"
    "QJ3AAECAxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRom"
    "JygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOEhYaHiIm"
    "KkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5e"
    "bn6Onq8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDzqiiivjj+kT//2Q=="
)


def make_frame(
        captured_at: datetime,
) -> FramePacket:
    return FramePacket(
        camera_id="camera-1",
        session_id="session-1",
        captured_at=captured_at,
        content_type="image/jpeg",
        data=TEST_JPEG,
    )


@pytest.mark.asyncio
async def test_real_recording_flow_creates_playable_mp4(
        tmp_path: Path,
) -> None:
    base = datetime.now(
        timezone.utc
    )

    ring_buffer_manager = (
        SessionFrameRingBufferManager(
            buffer_seconds=10,
            max_frames=300,
            max_bytes=64 * 1024 * 1024,
        )
    )

    await ring_buffer_manager.open_buffer(
        camera_id="camera-1",
        session_id="session-1",
    )

    # Pre-buffer için geçmiş frameler.
    # base - 5 saniye, preBufferSeconds=3
    # olduğu için recording'e dahil edilmemeli.
    for captured_at in (
            base - timedelta(seconds=5),
            base - timedelta(seconds=2),
            base - timedelta(seconds=1),
    ):
        await ring_buffer_manager.append_frame(
            camera_id="camera-1",
            session_id="session-1",
            frame=make_frame(
                captured_at
            ),
        )

    encoder = FfmpegVideoEncoder(
        output_dir=tmp_path,
    )

    recorder = EventRecorderCoordinator(
        video_encoder=encoder,
    )

    started = await recorder.start_recording(
        violation_id="violation-real-1",
        camera_id="camera-1",
        session_id="session-1",
        started_at=base,
        pre_buffer_seconds=3,
        post_buffer_seconds=2,
        max_clip_seconds=20,
        ring_buffer_manager=(
            ring_buffer_manager
        ),
    )

    assert (
            started.status
            == EventRecordingStatus.RECORDING
    )

    # base-2 ve base-1 recording'e girmeli.
    assert started.frame_count == 2

    # İhlal devam ederken gelen live frame.
    live_frame = make_frame(
        base + timedelta(seconds=1)
    )

    await ring_buffer_manager.append_frame(
        camera_id="camera-1",
        session_id="session-1",
        frame=live_frame,
    )

    await recorder.offer_frame(
        live_frame
    )

    # İhlal base+1 anında bitiyor.
    await recorder.request_stop(
        violation_id="violation-real-1",
        ended_at=(
                base + timedelta(seconds=1)
        ),
    )

    # postBufferSeconds=2 olduğundan
    # base+3'e kadar görüntü alınmalı.
    post_roll_frame = make_frame(
        base + timedelta(seconds=3)
    )

    await ring_buffer_manager.append_frame(
        camera_id="camera-1",
        session_id="session-1",
        frame=post_roll_frame,
    )

    await recorder.offer_frame(
        post_roll_frame
    )

    finished = (
        await recorder.wait_until_finalized(
            "violation-real-1"
        )
    )

    assert (
            finished.status
            == EventRecordingStatus.READY
    )

    assert finished.frame_count == 4

    assert finished.output_path is not None

    output_path = Path(
        finished.output_path
    )

    assert output_path.exists()
    assert output_path.suffix == ".mp4"
    assert output_path.stat().st_size > 0

    snapshot = await recorder.get_snapshot(
        "violation-real-1"
    )

    assert (
            snapshot.status
            == EventRecordingStatus.READY
    )

    assert snapshot.duration_ms is not None
    assert snapshot.duration_ms > 0

    assert snapshot.size_bytes is not None
    assert snapshot.size_bytes > 0

    await ring_buffer_manager.close_buffer(
        camera_id="camera-1",
        session_id="session-1",
    )

    await recorder.clear()