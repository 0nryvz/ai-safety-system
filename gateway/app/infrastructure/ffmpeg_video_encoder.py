import asyncio
import json
import re
import subprocess
import tempfile
from pathlib import Path
from typing import Sequence
from uuid import uuid4

from app.domain.frame import FramePacket
from app.services.video_encoder import (
    VideoEncoder,
    VideoEncodingError,
    VideoEncodingResult,
)


class FfmpegVideoEncoder(VideoEncoder):
    def __init__(
            self,
            output_dir: str | Path,
            ffmpeg_path: str = "ffmpeg",
            ffprobe_path: str = "ffprobe",
    ) -> None:
        self._output_dir = Path(output_dir)
        self._ffmpeg_path = ffmpeg_path
        self._ffprobe_path = ffprobe_path

    async def encode(
            self,
            violation_id: str,
            frames: Sequence[FramePacket],
    ) -> VideoEncodingResult:
        return await asyncio.to_thread(
            self._encode_sync,
            violation_id,
            tuple(frames),
        )

    def _encode_sync(
            self,
            violation_id: str,
            frames: tuple[FramePacket, ...],
    ) -> VideoEncodingResult:
        if not frames:
            raise VideoEncodingError(
                "Cannot encode an empty recording"
            )

        for frame in frames:
            content_type = (
                frame.content_type
                .split(";", 1)[0]
                .strip()
                .lower()
            )

            if content_type not in {
                "image/jpeg",
                "image/jpg",
            }:
                raise VideoEncodingError(
                    f"Unsupported frame content type: "
                    f"{frame.content_type}"
                )

            if not frame.data:
                raise VideoEncodingError(
                    "Recording contains an empty frame"
                )

        self._output_dir.mkdir(
            parents=True,
            exist_ok=True,
        )

        safe_violation_id = re.sub(
            r"[^A-Za-z0-9._-]+",
            "_",
            violation_id,
        ).strip("._-") or "violation"

        safe_violation_id = safe_violation_id[:80]

        output_path = (
                self._output_dir
                / f"{safe_violation_id}_{uuid4().hex}.mp4"
        ).resolve()

        fps = self._estimate_fps(frames)

        with tempfile.TemporaryDirectory(
                prefix="isg-recorder-",
        ) as temp_dir:
            temp_path = Path(temp_dir)

            for index, frame in enumerate(frames):
                frame_path = (
                        temp_path
                        / f"frame_{index:06d}.jpg"
                )

                frame_path.write_bytes(
                    frame.data
                )

            command = [
                self._ffmpeg_path,
                "-y",
                "-hide_banner",
                "-loglevel",
                "error",
                "-framerate",
                f"{fps:.6f}",
                "-start_number",
                "0",
                "-i",
                str(
                    temp_path
                    / "frame_%06d.jpg"
                ),
                "-an",
                "-vf",
                (
                    "scale=in_range=pc:out_range=tv,"
                    "pad=ceil(iw/2)*2:"
                    "ceil(ih/2)*2,"
                    "format=yuv420p"
                ),
                "-c:v",
                "libx264",
                "-preset",
                "veryfast",
                "-crf",
                "23",
                "-pix_fmt",
                "yuv420p",
                "-movflags",
                "+faststart",
                str(output_path),
            ]

            try:
                completed = subprocess.run(
                    command,
                    capture_output=True,
                    text=True,
                    check=False,
                    timeout=120,
                )

            except FileNotFoundError as exc:
                raise VideoEncodingError(
                    "FFmpeg executable was not found: "
                    f"{self._ffmpeg_path}"
                ) from exc

            except subprocess.TimeoutExpired as exc:
                raise VideoEncodingError(
                    "FFmpeg encoding timed out"
                ) from exc

            if completed.returncode != 0:
                output_path.unlink(
                    missing_ok=True,
                )

                stderr = (
                        completed.stderr.strip()
                        or "unknown FFmpeg error"
                )

                raise VideoEncodingError(
                    "FFmpeg failed with exit code "
                    f"{completed.returncode}: "
                    f"{stderr}"
                )

        probe = self._probe_output(
            output_path
        )

        codec_name = probe["codec_name"]
        pixel_format = probe["pixel_format"]
        duration_ms = probe["duration_ms"]

        if codec_name != "h264":
            output_path.unlink(
                missing_ok=True,
            )

            raise VideoEncodingError(
                "Encoded clip is not H.264: "
                f"{codec_name}"
            )

        if pixel_format != "yuv420p":
            output_path.unlink(
                missing_ok=True,
            )

            raise VideoEncodingError(
                "Encoded clip is not yuv420p: "
                f"{pixel_format}"
            )

        return VideoEncodingResult(
            output_path=output_path,
            frame_count=len(frames),
            duration_ms=duration_ms,
            size_bytes=output_path.stat().st_size,
            codec_name=codec_name,
            pixel_format=pixel_format,
            fps=fps,
        )

    def _probe_output(
            self,
            output_path: Path,
    ) -> dict[str, object]:
        command = [
            self._ffprobe_path,
            "-v",
            "error",
            "-select_streams",
            "v:0",
            "-show_entries",
            (
                "stream=codec_name,pix_fmt:"
                "format=duration"
            ),
            "-of",
            "json",
            str(output_path),
        ]

        try:
            completed = subprocess.run(
                command,
                capture_output=True,
                text=True,
                check=False,
                timeout=30,
            )

        except FileNotFoundError as exc:
            output_path.unlink(
                missing_ok=True,
            )

            raise VideoEncodingError(
                "FFprobe executable was not found: "
                f"{self._ffprobe_path}"
            ) from exc

        except subprocess.TimeoutExpired as exc:
            output_path.unlink(
                missing_ok=True,
            )

            raise VideoEncodingError(
                "FFprobe verification timed out"
            ) from exc

        if completed.returncode != 0:
            output_path.unlink(
                missing_ok=True,
            )

            stderr = (
                    completed.stderr.strip()
                    or "unknown FFprobe error"
            )

            raise VideoEncodingError(
                "FFprobe failed with exit code "
                f"{completed.returncode}: "
                f"{stderr}"
            )

        try:
            payload = json.loads(
                completed.stdout
            )

            stream = payload[
                "streams"
            ][0]

            duration_seconds = float(
                payload["format"]["duration"]
            )

            codec_name = str(
                stream["codec_name"]
            )

            pixel_format = str(
                stream["pix_fmt"]
            )

        except (
                KeyError,
                IndexError,
                TypeError,
                ValueError,
                json.JSONDecodeError,
        ) as exc:
            output_path.unlink(
                missing_ok=True,
            )

            raise VideoEncodingError(
                "FFprobe returned an invalid "
                "verification payload"
            ) from exc

        return {
            "codec_name": codec_name,
            "pixel_format": pixel_format,
            "duration_ms": max(
                1,
                round(
                    duration_seconds * 1000
                ),
            ),
        }

    @staticmethod
    def _estimate_fps(
            frames: tuple[FramePacket, ...],
    ) -> float:
        if len(frames) < 2:
            return 15.0

        duration_seconds = (
                frames[-1].captured_at
                - frames[0].captured_at
        ).total_seconds()

        if duration_seconds <= 0:
            return 15.0

        fps = (
                (len(frames) - 1)
                / duration_seconds
        )

        return min(
            60.0,
            max(1.0, fps),
        )