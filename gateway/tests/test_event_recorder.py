import asyncio
from datetime import (
    datetime,
    timedelta,
    timezone,
)
from pathlib import Path
from typing import Sequence

import pytest

from app.domain.frame import FramePacket
from app.services.event_recorder import (
    EventRecorderCoordinator,
    EventRecordingStatus,
)
from app.services.clip_delivery_coordinator import (
    ClipDeliveryCommand,
    ClipDeliveryError,
)
from app.services.clip_storage import (
    ClipStorageResult,
)
from app.services.video_encoder import (
    VideoEncodingResult,
)


class FakeVideoEncoder:
    def __init__(self) -> None:
        self.calls: list[
            tuple[
                str,
                tuple[FramePacket, ...],
            ]
        ] = []

    async def encode(
            self,
            violation_id: str,
            frames: Sequence[FramePacket],
    ) -> VideoEncodingResult:
        captured_frames = tuple(
            frames
        )

        self.calls.append(
            (
                violation_id,
                captured_frames,
            )
        )

        return VideoEncodingResult(
            output_path=Path(
                f"/tmp/{violation_id}.mp4"
            ),
            frame_count=len(
                captured_frames
            ),
            duration_ms=1000,
            size_bytes=1234,
            codec_name="h264",
            pixel_format="yuv420p",
            fps=15.0,
        )


class StubRingBufferManager:
    def __init__(
            self,
            frames: tuple[
                FramePacket,
                ...
            ],
    ) -> None:
        self._frames = frames

    async def snapshot(
            self,
            camera_id: str,
            session_id: str,
    ) -> tuple[
        FramePacket,
        ...
    ]:
        return tuple(
            frame
            for frame in self._frames
            if (
                    frame.camera_id == camera_id
                    and frame.session_id == session_id
            )
        )


class FakeClipDeliveryCoordinator:
    def __init__(
            self,
            *,
            started_event: asyncio.Event | None = None,
            release_event: asyncio.Event | None = None,
            raise_error: Exception | None = None,
    ) -> None:
        self._started_event = started_event
        self._release_event = release_event
        self._raise_error = raise_error
        self.calls: list[ClipDeliveryCommand] = []

    async def deliver_ready(
            self,
            command: ClipDeliveryCommand,
    ) -> ClipStorageResult:
        self.calls.append(command)

        if self._started_event is not None:
            self._started_event.set()

        if self._release_event is not None:
            await self._release_event.wait()

        if self._raise_error is not None:
            raise self._raise_error

        return ClipStorageResult(
            bucket="private-recordings",
            object_key=(
                "violations/2026/08/"
                f"{command.violation_id}/{command.recording_id}.mp4"
            ),
            checksum="sha256:abcd",
            size_bytes=command.size_bytes,
        )


class FailingVideoEncoder:
    async def encode(
            self,
            violation_id: str,
            frames: Sequence[FramePacket],
    ) -> VideoEncodingResult:
        raise RuntimeError("encode boom")


def make_frame(
        captured_at: datetime,
        *,
        camera_id: str = "camera-1",
        session_id: str = "session-1",
) -> FramePacket:
    return FramePacket(
        camera_id=camera_id,
        session_id=session_id,
        captured_at=captured_at,
        content_type="image/jpeg",
        data=b"jpeg-data",
    )


@pytest.mark.asyncio
async def test_recorder_uses_prebuffer_live_frames_and_post_roll(
) -> None:
    base = datetime.now(
        timezone.utc
    )

    encoder = FakeVideoEncoder()

    recorder = EventRecorderCoordinator(
        video_encoder=encoder,
    )

    ring_buffer = StubRingBufferManager(
        frames=(
            make_frame(
                base - timedelta(seconds=5)
            ),
            make_frame(
                base - timedelta(seconds=2)
            ),
            make_frame(
                base - timedelta(seconds=1)
            ),
        )
    )

    snapshot = await recorder.start_recording(
        recording_id="recording-1",
        violation_id="violation-1",
        camera_id="camera-1",
        session_id="session-1",
        started_at=base,
        pre_buffer_seconds=3,
        post_buffer_seconds=2,
        max_clip_seconds=20,
        ring_buffer_manager=ring_buffer,
    )

    assert (
            snapshot.status
            == EventRecordingStatus.RECORDING
    )
    assert snapshot.recording_id == "recording-1"

    # -5 saniyelik frame pre-buffer
    # penceresinin dışında kalmalı.
    assert snapshot.frame_count == 2

    await recorder.offer_frame(
        make_frame(
            base + timedelta(seconds=1)
        )
    )

    await recorder.request_stop(
        violation_id="violation-1",
        ended_at=(
                base + timedelta(seconds=1)
        ),
    )

    # STOP + 2 saniye = base + 3
    await recorder.offer_frame(
        make_frame(
            base + timedelta(seconds=3)
        )
    )

    finished = (
        await recorder.wait_until_finalized(
            "violation-1"
        )
    )

    assert (
            finished.status
            == EventRecordingStatus.READY
    )
    assert finished.recording_id == "recording-1"

    assert finished.frame_count == 4

    assert (
            Path(finished.output_path)
            == Path("/tmp/violation-1.mp4")
    )

    assert len(
        encoder.calls
    ) == 1

    encoded_frames = (
        encoder.calls[0][1]
    )

    assert [
               frame.captured_at
               for frame in encoded_frames
           ] == [
               base - timedelta(seconds=2),
               base - timedelta(seconds=1),
               base + timedelta(seconds=1),
               base + timedelta(seconds=3),
               ]


@pytest.mark.asyncio
async def test_recorder_enforces_max_clip_deadline(
) -> None:
    base = datetime.now(
        timezone.utc
    )

    encoder = FakeVideoEncoder()

    recorder = EventRecorderCoordinator(
        video_encoder=encoder,
    )

    ring_buffer = StubRingBufferManager(
        frames=(
            make_frame(
                base - timedelta(seconds=2)
            ),
        )
    )

    await recorder.start_recording(
        recording_id="recording-2",
        violation_id="violation-2",
        camera_id="camera-1",
        session_id="session-1",
        started_at=base,
        pre_buffer_seconds=2,
        post_buffer_seconds=3,
        max_clip_seconds=5,
        ring_buffer_manager=ring_buffer,
    )

    # clip_started_at = base - 2
    # maxClipSeconds = 5
    # hard deadline = base + 3

    await recorder.offer_frame(
        make_frame(
            base + timedelta(seconds=2)
        )
    )

    await recorder.offer_frame(
        make_frame(
            base + timedelta(seconds=3)
        )
    )

    finished = (
        await recorder.wait_until_finalized(
            "violation-2"
        )
    )

    assert (
            finished.status
            == EventRecordingStatus.READY
    )
    assert finished.recording_id == "recording-2"

    assert len(
        encoder.calls
    ) == 1

    assert (
            encoder.calls[0][1][-1].captured_at
            == base + timedelta(seconds=3)
    )


@pytest.mark.asyncio
async def test_session_finalize_forces_active_clip_to_finish(
) -> None:
    base = datetime.now(
        timezone.utc
    )

    encoder = FakeVideoEncoder()

    recorder = EventRecorderCoordinator(
        video_encoder=encoder,
    )

    ring_buffer = StubRingBufferManager(
        frames=(
            make_frame(
                base - timedelta(seconds=1)
            ),
        )
    )

    await recorder.start_recording(
        recording_id="recording-3",
        violation_id="violation-3",
        camera_id="camera-1",
        session_id="session-1",
        started_at=base,
        pre_buffer_seconds=2,
        post_buffer_seconds=3,
        max_clip_seconds=20,
        ring_buffer_manager=ring_buffer,
    )

    finalized_count = (
        await recorder.finalize_session(
            camera_id="camera-1",
            session_id="session-1",
        )
    )

    finished = (
        await recorder.get_snapshot(
            "violation-3"
        )
    )

    assert finalized_count == 1

    assert (
            finished.status
            == EventRecordingStatus.READY
    )
    assert finished.recording_id == "recording-3"


@pytest.mark.asyncio
async def test_recorder_waits_for_delivery_before_ready_and_uses_uploading_status(
) -> None:
    base = datetime.now(
        timezone.utc
    )
    started_event = asyncio.Event()
    release_event = asyncio.Event()

    encoder = FakeVideoEncoder()
    delivery = FakeClipDeliveryCoordinator(
        started_event=started_event,
        release_event=release_event,
    )

    recorder = EventRecorderCoordinator(
        video_encoder=encoder,
        clip_delivery_coordinator=delivery,
    )

    ring_buffer = StubRingBufferManager(
        frames=(
            make_frame(base - timedelta(seconds=2)),
            make_frame(base - timedelta(seconds=1)),
        )
    )

    await recorder.start_recording(
        recording_id="recording-upload-1",
        violation_id="violation-upload-1",
        camera_id="camera-1",
        session_id="session-1",
        started_at=base,
        pre_buffer_seconds=3,
        post_buffer_seconds=2,
        max_clip_seconds=20,
        ring_buffer_manager=ring_buffer,
    )

    await recorder.offer_frame(
        make_frame(base + timedelta(seconds=1))
    )

    await recorder.request_stop(
        violation_id="violation-upload-1",
        ended_at=(base + timedelta(seconds=1)),
    )

    await recorder.offer_frame(
        make_frame(base + timedelta(seconds=3))
    )

    await asyncio.wait_for(
        started_event.wait(),
        timeout=1.0,
    )

    uploading = await recorder.get_snapshot(
        "violation-upload-1"
    )

    assert (
            uploading.status
            == EventRecordingStatus.UPLOADING
    )
    assert uploading.frame_count == 4
    assert uploading.duration_ms == 1000
    assert uploading.size_bytes == 1234

    release_event.set()

    finished = await recorder.wait_until_finalized(
        "violation-upload-1"
    )

    assert (
            finished.status
            == EventRecordingStatus.READY
    )

    assert len(delivery.calls) == 1

    call = delivery.calls[0]

    assert call.recording_id == "recording-upload-1"
    assert call.violation_id == "violation-upload-1"
    assert call.started_at == base
    assert call.output_path == Path("/tmp/violation-upload-1.mp4")
    assert call.duration_ms == 1000
    assert call.size_bytes == 1234


@pytest.mark.asyncio
async def test_delivery_error_marks_recording_error(
) -> None:
    base = datetime.now(
        timezone.utc
    )

    encoder = FakeVideoEncoder()
    delivery = FakeClipDeliveryCoordinator(
        raise_error=ClipDeliveryError(
            "Recording READY callback failed"
        ),
    )

    recorder = EventRecorderCoordinator(
        video_encoder=encoder,
        clip_delivery_coordinator=delivery,
    )

    ring_buffer = StubRingBufferManager(
        frames=(
            make_frame(base - timedelta(seconds=1)),
        )
    )

    await recorder.start_recording(
        recording_id="recording-upload-2",
        violation_id="violation-upload-2",
        camera_id="camera-1",
        session_id="session-1",
        started_at=base,
        pre_buffer_seconds=2,
        post_buffer_seconds=2,
        max_clip_seconds=20,
        ring_buffer_manager=ring_buffer,
    )

    await recorder.request_stop(
        violation_id="violation-upload-2",
        ended_at=(base + timedelta(seconds=1)),
    )

    await recorder.offer_frame(
        make_frame(base + timedelta(seconds=3))
    )

    finished = await recorder.wait_until_finalized(
        "violation-upload-2"
    )

    assert (
            finished.status
            == EventRecordingStatus.ERROR
    )
    assert finished.error is not None
    assert "ClipDeliveryError" in finished.error
    assert len(delivery.calls) == 1


@pytest.mark.asyncio
async def test_encoder_error_does_not_call_delivery(
) -> None:
    base = datetime.now(
        timezone.utc
    )

    encoder = FailingVideoEncoder()
    delivery = FakeClipDeliveryCoordinator()

    recorder = EventRecorderCoordinator(
        video_encoder=encoder,
        clip_delivery_coordinator=delivery,
    )

    ring_buffer = StubRingBufferManager(
        frames=(
            make_frame(base - timedelta(seconds=1)),
        )
    )

    await recorder.start_recording(
        recording_id="recording-upload-3",
        violation_id="violation-upload-3",
        camera_id="camera-1",
        session_id="session-1",
        started_at=base,
        pre_buffer_seconds=2,
        post_buffer_seconds=2,
        max_clip_seconds=20,
        ring_buffer_manager=ring_buffer,
    )

    await recorder.request_stop(
        violation_id="violation-upload-3",
        ended_at=(base + timedelta(seconds=1)),
    )

    await recorder.offer_frame(
        make_frame(base + timedelta(seconds=3))
    )

    finished = await recorder.wait_until_finalized(
        "violation-upload-3"
    )

    assert (
            finished.status
            == EventRecordingStatus.ERROR
    )
    assert finished.error is not None
    assert "RuntimeError" in finished.error
    assert len(delivery.calls) == 0