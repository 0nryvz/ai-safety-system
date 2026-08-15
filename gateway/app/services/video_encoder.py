from dataclasses import dataclass
from pathlib import Path
from typing import Protocol, Sequence

from app.domain.frame import FramePacket


class VideoEncodingError(RuntimeError):
    """Video encoder could not create or verify the output clip."""


@dataclass(frozen=True, slots=True)
class VideoEncodingResult:
    output_path: Path
    frame_count: int
    duration_ms: int
    size_bytes: int
    codec_name: str
    pixel_format: str
    fps: float


class VideoEncoder(Protocol):
    async def encode(
            self,
            violation_id: str,
            frames: Sequence[FramePacket],
    ) -> VideoEncodingResult:
        ...