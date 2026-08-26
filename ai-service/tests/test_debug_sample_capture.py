import json
from pathlib import Path

import pytest

from app import debug_sample_capture as capture


@pytest.fixture
def capture_dir(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> Path:
    debug_dir = tmp_path / "debug_frames"
    debug_dir.mkdir()
    monkeypatch.setattr(capture, "DEBUG_DIR", debug_dir)
    monkeypatch.setattr(capture, "TRIGGER_PATH", debug_dir / "capture_5.trigger")
    capture.reset_debug_capture_state()
    yield debug_dir
    capture.reset_debug_capture_state()


def _payload(n: int) -> dict:
    return {"eventId": f"evt-{n}", "detections": []}


def test_no_trigger_writes_nothing(capture_dir: Path) -> None:
    capture.maybe_capture_debug_sample(b"jpeg-1", _payload(1))

    assert list(capture_dir.glob("sample_*")) == []


def test_trigger_saves_five_with_interval(
    capture_dir: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    trigger = capture_dir / "capture_5.trigger"
    trigger.write_text("", encoding="utf-8")

    clock = {"now": 100.0}
    monkeypatch.setattr(capture.time, "monotonic", lambda: clock["now"])

    for i in range(1, 6):
        capture.maybe_capture_debug_sample(f"jpeg-{i}".encode(), _payload(i))
        jpg = capture_dir / f"sample_{i:02d}.jpg"
        payload_path = capture_dir / f"sample_{i:02d}_payload.json"
        assert jpg.read_bytes() == f"jpeg-{i}".encode()
        assert json.loads(payload_path.read_text(encoding="utf-8")) == _payload(i)
        clock["now"] += 2.0

    assert not trigger.exists()
    assert len(list(capture_dir.glob("sample_*.jpg"))) == 5


def test_interval_skips_until_two_seconds(
    capture_dir: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    (capture_dir / "capture_5.trigger").write_text("", encoding="utf-8")
    clock = {"now": 0.0}
    monkeypatch.setattr(capture.time, "monotonic", lambda: clock["now"])

    capture.maybe_capture_debug_sample(b"first", _payload(1))
    clock["now"] = 1.9
    capture.maybe_capture_debug_sample(b"too-soon", _payload(99))

    assert (capture_dir / "sample_01.jpg").read_bytes() == b"first"
    assert not (capture_dir / "sample_02.jpg").exists()

    clock["now"] = 2.0
    capture.maybe_capture_debug_sample(b"second", _payload(2))
    assert (capture_dir / "sample_02.jpg").read_bytes() == b"second"


def test_new_trigger_clears_old_samples(
    capture_dir: Path, monkeypatch: pytest.MonkeyPatch
) -> None:
    monkeypatch.setattr(capture.time, "monotonic", lambda: 0.0)
    (capture_dir / "sample_01.jpg").write_bytes(b"stale")
    (capture_dir / "sample_01_payload.json").write_text("{}", encoding="utf-8")
    (capture_dir / "capture_5.trigger").write_text("", encoding="utf-8")

    capture.maybe_capture_debug_sample(b"fresh", _payload(1))

    assert (capture_dir / "sample_01.jpg").read_bytes() == b"fresh"
