import subprocess
import tempfile
import time
from datetime import (
    datetime,
    timedelta,
    timezone,
)
from pathlib import Path

import httpx


BASE_URL = "http://127.0.0.1:8000"

CAMERA_ID = "camera-real-test"
SESSION_ID = "session-real-test"
SESSION_TOKEN = "dev-session-token"

VIOLATION_ID = "violation-real-test"
START_COMMAND_ID = "start-real-test"
STOP_COMMAND_ID = "stop-real-test"

FPS = 15

START_AT_SECONDS = 5
STOP_AT_SECONDS = 12

PRE_BUFFER_SECONDS = 3
POST_BUFFER_SECONDS = 3
MAX_CLIP_SECONDS = 20

VIDEO_PATH = Path("test-video.mp4")

OUTPUT_DIR = Path("var/recordings")


def extract_frames(
        video_path: Path,
        output_dir: Path,
) -> list[Path]:

    command = [
        "ffmpeg",
        "-y",
        "-hide_banner",
        "-loglevel",
        "error",
        "-i",
        str(video_path),
        "-vf",
        f"fps={FPS}",
        "-q:v",
        "2",
        str(
            output_dir
            / "frame_%06d.jpg"
        ),
    ]

    subprocess.run(
        command,
        check=True,
    )

    return sorted(
        output_dir.glob(
            "frame_*.jpg"
        )
    )


def open_session(
        client: httpx.Client,
) -> None:

    response = client.post(
        f"{BASE_URL}/api/v1/sessions/open",
        json={
            "cameraId": CAMERA_ID,
            "sessionId": SESSION_ID,
            "sessionToken": SESSION_TOKEN,
        },
    )

    response.raise_for_status()

    print(
        "SESSION OPEN:",
        response.status_code,
        response.json(),
    )


def send_frame(
        client: httpx.Client,
        frame_path: Path,
        captured_at: datetime,
) -> None:

    frame_bytes = frame_path.read_bytes()

    response = client.post(
        (
            f"{BASE_URL}/api/v1/sessions/"
            f"{SESSION_ID}/frames"
        ),
        content=frame_bytes,
        headers={
            "Content-Type": "image/jpeg",
            "X-Camera-Id": CAMERA_ID,
            "X-Frame-Timestamp": (
                captured_at.isoformat()
            ),
        },
    )

    response.raise_for_status()


def send_start(
        client: httpx.Client,
        started_at: datetime,
) -> None:

    response = client.post(
        (
            f"{BASE_URL}/internal/v1/"
            "recordings/commands/start"
        ),
        json={
            "commandId": START_COMMAND_ID,
            "violationId": VIOLATION_ID,
            "cameraId": CAMERA_ID,
            "sessionId": SESSION_ID,
            "startedAt": (
                started_at.isoformat()
            ),
            "preBufferSeconds": (
                PRE_BUFFER_SECONDS
            ),
            "postBufferSeconds": (
                POST_BUFFER_SECONDS
            ),
            "maxClipSeconds": (
                MAX_CLIP_SECONDS
            ),
        },
    )

    response.raise_for_status()

    print(
        "\nSTART:",
        response.status_code,
        response.json(),
    )


def send_stop(
        client: httpx.Client,
        ended_at: datetime,
) -> None:

    response = client.post(
        (
            f"{BASE_URL}/internal/v1/"
            "recordings/commands/stop"
        ),
        json={
            "commandId": STOP_COMMAND_ID,
            "violationId": VIOLATION_ID,
            "endedAt": (
                ended_at.isoformat()
            ),
        },
    )

    response.raise_for_status()

    print(
        "\nSTOP:",
        response.status_code,
        response.json(),
    )


def close_session(
        client: httpx.Client,
) -> None:

    response = client.post(
        (
            f"{BASE_URL}/api/v1/sessions/"
            f"{SESSION_ID}/close"
        ),
        json={
            "cameraId": CAMERA_ID,
        },
    )

    response.raise_for_status()

    print(
        "\nSESSION CLOSE:",
        response.status_code,
    )


def main() -> None:

    if not VIDEO_PATH.exists():
        raise FileNotFoundError(
            f"Video bulunamadı: "
            f"{VIDEO_PATH.resolve()}"
        )

    OUTPUT_DIR.mkdir(
        parents=True,
        exist_ok=True,
    )

    existing_recordings = set(
        OUTPUT_DIR.glob("*.mp4")
    )

    print(
        "Video:",
        VIDEO_PATH.resolve(),
    )

    print(
        "JPEG frameler çıkarılıyor..."
    )

    with tempfile.TemporaryDirectory(
            prefix="isg-real-video-test-"
    ) as temp_dir:

        temp_path = Path(temp_dir)

        frames = extract_frames(
            video_path=VIDEO_PATH,
            output_dir=temp_path,
        )

        if not frames:
            raise RuntimeError(
                "Videodan hiç frame çıkarılamadı"
            )

        video_duration_seconds = (
                len(frames) / FPS
        )

        print(
            f"{len(frames)} frame hazır"
        )

        print(
            "Yaklaşık video süresi:",
            f"{video_duration_seconds:.2f} sn",
        )

        required_duration = (
                STOP_AT_SECONDS
                + POST_BUFFER_SECONDS
        )

        if (
                video_duration_seconds
                < required_duration
        ):
            raise RuntimeError(
                "Test videosu çok kısa. "
                f"En az {required_duration} saniye "
                "olmalı."
            )

        stream_started_at = (
            datetime.now(
                timezone.utc
            )
        )

        start_sent = False
        stop_sent = False

        with httpx.Client(
                timeout=10.0,
        ) as client:

            open_session(
                client
            )

            print(
                "\nVideo Gateway'e "
                f"{FPS} FPS gönderiliyor..."
            )

            wall_clock_start = (
                time.perf_counter()
            )

            for index, frame_path in enumerate(
                    frames
            ):

                elapsed_seconds = (
                        index / FPS
                )

                captured_at = (
                        stream_started_at
                        + timedelta(
                    seconds=(
                        elapsed_seconds
                    )
                )
                )

                target_wall_time = (
                        wall_clock_start
                        + elapsed_seconds
                )

                sleep_seconds = (
                        target_wall_time
                        - time.perf_counter()
                )

                if sleep_seconds > 0:
                    time.sleep(
                        sleep_seconds
                    )

                send_frame(
                    client=client,
                    frame_path=frame_path,
                    captured_at=captured_at,
                )

                if (
                        not start_sent
                        and elapsed_seconds
                        >= START_AT_SECONDS
                ):
                    send_start(
                        client=client,
                        started_at=captured_at,
                    )

                    start_sent = True

                if (
                        not stop_sent
                        and elapsed_seconds
                        >= STOP_AT_SECONDS
                ):
                    send_stop(
                        client=client,
                        ended_at=captured_at,
                    )

                    stop_sent = True

                if index % FPS == 0:
                    print(
                        f"\rGönderilen süre: "
                        f"{elapsed_seconds:.0f} sn",
                        end="",
                        flush=True,
                    )

                if (
                        stop_sent
                        and elapsed_seconds
                        >= (
                        STOP_AT_SECONDS
                        + POST_BUFFER_SECONDS
                        + 1
                )
                ):
                    break

            print()

            time.sleep(
                1.0
            )

            close_session(
                client
            )

    time.sleep(
        1.0
    )

    current_recordings = set(
        OUTPUT_DIR.glob("*.mp4")
    )

    new_recordings = sorted(
        current_recordings
        - existing_recordings,
        key=lambda path: (
            path.stat().st_mtime
        ),
        )

    if not new_recordings:
        raise RuntimeError(
            "Yeni MP4 bulunamadı. "
            "Gateway loglarını kontrol et."
        )

    newest_recording = (
        new_recordings[-1]
    )

    print(
        "\nTEST BAŞARILI"
    )

    print(
        "Oluşan klip:",
        newest_recording.resolve(),
    )

    print(
        "Boyut:",
        newest_recording.stat().st_size,
        "bytes",
    )


if __name__ == "__main__":
    main()