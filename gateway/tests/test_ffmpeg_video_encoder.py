import base64
from datetime import (
    datetime,
    timedelta,
    timezone,
)

import pytest

from app.domain.frame import FramePacket
from app.infrastructure.ffmpeg_video_encoder import (
    FfmpegVideoEncoder,
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
async def test_ffmpeg_encoder_creates_h264_mp4(
        tmp_path,
) -> None:
    base = datetime.now(
        timezone.utc
    )

    frames = tuple(
        make_frame(
            base
            + timedelta(
                milliseconds=index * 100
            )
        )
        for index in range(10)
    )

    encoder = FfmpegVideoEncoder(
        output_dir=tmp_path,
    )

    result = await encoder.encode(
        violation_id="violation-test-1",
        frames=frames,
    )

    assert result.output_path.exists()
    assert result.output_path.suffix == ".mp4"

    assert result.frame_count == 10
    assert result.size_bytes > 0
    assert result.duration_ms > 0

    assert result.codec_name == "h264"
    assert result.pixel_format == "yuv420p"

    assert result.fps > 0