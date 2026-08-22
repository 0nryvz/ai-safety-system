# HOTFIX — Mobile → Gateway Frame Quality + ≥5 FPS Hardening

**Priority:** P0 / real-device E2E blocker  
**Suggested model:** STRONG (Claude Opus 5 High or equivalent)  
**Owner:** Onur / Mobile-Gateway integration branch  
**Primary scope:** `mobile/**`  
**Read-only verification:** `gateway/**`, `ai-service/**`  
**Do not change:** Backend, violation rules, recorder contracts, AI model weights/preprocessing unless a hard STOP condition is proven.

---

## 0. Goal

Fix the real-device Mobile → Gateway media path so that:

1. Gateway no longer receives ~160px-wide, visibly degraded JPEG frames.
2. Frames have enough spatial detail for the current AI Worker/model.
3. The phone sustains **at least 5 successfully accepted JPEG frames/sec** in the real Gateway test.
4. Prefer a practical target of **8–15 accepted FPS** when the device/network can sustain it.
5. No stale backlog, unbounded queue, session regression, reconnect regression, or camera lifecycle regression is introduced.
6. Gateway must continue sampling AI frames at its own configured ~3 FPS. Do **not** move AI sampling to mobile.

---

## 1. Repo is source of truth

Before coding, inspect the current branch. Do not assume paths or behavior from old docs if current code differs.

Read first:

- `mobile/lib/core/config/app_config.dart`
- `mobile/lib/features/streaming/camera_frame_service.dart`
- `mobile/lib/features/streaming/native_jpeg_encoder.dart`
- `mobile/lib/features/streaming/streaming_controller.dart`
- `mobile/lib/core/network/api_client.dart`
- `mobile/android/app/src/main/kotlin/**/MainActivity.kt`
- `mobile/lib/features/camera/camera_page.dart`
- relevant mobile tests

Read-only:

- `gateway/app/api/routes/frames.py`
- `gateway/app/core/config.py`
- `gateway/app/services/session_ai_frame_sampler.py`
- `gateway/app/api/routes/metrics.py`
- `ai-service/app/services/model_runner.py`
- `ai-service/models/args.yaml`

If these files have materially changed, adapt this task to the current code instead of forcing an obsolete design.

---

## 2. Verified root-cause candidates in the supplied snapshot

The current snapshot has multiple quality bottlenecks at the same time:

### 2.1 Camera stream starts at low resolution

`StreamingController.initializeCamera()` creates:

```dart
CameraController(
  ...,
  ResolutionPreset.low,
  ...
)
```

This limits source detail before JPEG encoding begins.

### 2.2 Mobile intentionally downsamples toward 160 px

`AppConfig.targetEncodeWidth` currently defaults to:

```dart
ENCODE_WIDTH = 160
```

and degraded width is `128`.

`CameraFrameService.extractFrame()` derives an integer `step` from source width / target width. The native encoder then outputs approximately:

```text
sourceWidth / step
sourceHeight / step
```

This is consistent with the observed ~160px Gateway image.

### 2.3 JPEG quality is very low for PPE detection

Current default:

```dart
JPEG_QUALITY = 40
```

For small PPE objects such as gloves/mask/jacket details, low spatial resolution + aggressive JPEG compression destroys useful information.

### 2.4 Current integer-step downsample is too coarse

The native encoder downsamples by an integer `step`.

Example problem:

```text
source width = 720
desired width = 640
ceil(720 / 640) = 2
actual output ≈ 360
```

A target of 640 can therefore unexpectedly produce a much smaller image.

### 2.5 Existing `sendFps` is not necessarily Gateway-accepted FPS

In the current controller, the send-FPS timestamp is recorded after encode and before the HTTP upload result is known.

For this task, the acceptance metric must be:

> **successful HTTP 202 frame uploads per second**

not “encode completed per second”.

### 2.6 AI Worker currently performs inference at `imgsz=640`

Do not change the AI model just to compensate for a 160px mobile input. The mobile/Gateway frame should preserve substantially more source information before the AI Worker resizes/letterboxes it.

---

## 3. Architecture guardrails

Keep the existing architecture:

```text
Phone Camera
   ↓
Flutter image stream
   ↓
native JPEG encoder
   ↓
HTTP JPEG upload
   ↓
Camera Ingestion Gateway
   ↓
ring buffer + Gateway-side ~3 FPS sampling
   ↓
AI Worker
```

Rules:

- Mobile sends JPEG only to Gateway.
- Do not send media directly to AI Worker or Spring Backend.
- Do not move ring buffer or AI sampling into Flutter.
- Do not redesign camera/session token handling.
- Do not rewrite session/reconnect/lifecycle logic unless the frame-quality change reveals a direct regression.
- Keep bounded frame work. If processing cannot keep up, **drop stale/new frames deliberately instead of building backlog**.
- Do not raise Gateway frame limit unless a real 413 is observed and the JPEG is otherwise reasonable.

Current Gateway frame-size limit in the supplied snapshot is **2 MiB**, which should normally be ample for a sensible 480p/640-class JPEG.

---

## 4. Required implementation strategy

### Phase A — Instrument first

Before tuning quality, make the pipeline measurable.

Add diagnostics behind the existing `FRAME_DIAGNOSTICS` flag (or an equally scoped diagnostic mechanism). Do not spam production logs.

At minimum record:

- source camera frame width × height
- encoded JPEG width × height
- JPEG byte size
- JPEG quality
- encode duration ms
- upload duration ms
- successful Gateway-accepted FPS
- failed/drop counts
- active encode count
- in-flight/queued upload count if available

Do not log raw frame bytes, camera key/session token, JWT, or secrets.

Make `sendFps` represent **successful Gateway accepted frames**, or add a clearly named `acceptedFps` metric and use that for the ≥5 FPS acceptance gate.

### Phase B — Stop destroying resolution

Do not simply change `ENCODE_WIDTH=160` to `640` and call the task finished.

Fix both source and encoder behavior.

#### Camera source

Raise `ResolutionPreset.low` to a measured real-device profile that preserves enough detail.

Start evaluation with:

```text
ResolutionPreset.medium
```

If the real phone still produces insufficient source resolution and performance permits it, evaluate `high`.

Do not select `max`/`ultraHigh` blindly.

#### Output target

Initial quality target:

```text
encoded width: about 640 px
JPEG quality: about 60–70
```

Preserve aspect ratio. Never upscale beyond actual source dimensions.

The implementation must support source dimensions such as:

```text
640×480
720×480
1280×720
1920×1080
```

without the current coarse “720 target 640 → 360” integer-step collapse.

### Phase C — Fix the native resize/downsample plan

Keep expensive pixel work off the Dart UI isolate.

The current Android native path may remain, but change it from coarse integer-step sizing to an explicit output size.

Desired behavior:

```text
targetWidth = min(configuredTargetWidth, sourceWidth)
targetHeight = aspect-ratio-preserving even number
```

Examples:

```text
640×480  -> 640×480
720×480  -> ~640×426 (even dimensions)
1280×720 -> 640×360
1920×1080 -> 640×360
```

The exact implementation can use a native YUV/NV21 resampling strategy, but:

- output dimensions must be deterministic;
- dimensions must be even where NV21 requires it;
- Y/U/V indexing must remain correct for YUV_420_888 row/pixel strides;
- do not introduce a full-resolution Dart pixel loop;
- do not allocate unbounded buffers per frame;
- maintain the existing bounded native executor behavior.

If changing the native resampler is too risky for the deadline, STOP and propose the safest measurable alternative using a camera source preset whose native dimensions can be encoded without destructive downsampling.

### Phase D — FPS/backpressure tuning

The hard floor is:

```text
Gateway-accepted FPS >= 5
```

Do not hard-cap the app to 5 FPS. Prefer 8–15 if stable.

Start with a quality-first profile around:

```text
source preset: medium
encoded width: 640
JPEG quality: 60–65
paced/target FPS: 10 (or current 15 if the measured phone sustains it)
minimum accepted FPS: 5
```

Tune based on measurement, not guesses.

If 640-class frames cannot sustain 5 accepted FPS:

1. reduce quality moderately before destroying resolution;
2. then consider an encoded-width fallback around 480;
3. keep the floor suitable for AI recognition;
4. never silently fall back to the old 160/128 widths.

Do not create a complex adaptive-quality subsystem unless the real device actually needs it. A simple, tested profile is preferable for MVP.

If adaptive fallback is needed, it must be bounded and hysteresis-based, for example:

- downgrade only after accepted FPS remains `<5` for several seconds;
- upgrade only after sustained recovery;
- no oscillation every second;
- no session restart merely to change JPEG profile.

### Phase E — Protect upload behavior

Keep/verify:

- HTTP keep-alive
- bounded timeout
- bounded encode concurrency
- bounded upload concurrency
- no unbounded stale JPEG queue
- stale frame dropping when producer outruns consumer

Higher resolution must not create a growing queue.

Gateway `/metrics` should remain healthy:

- `queued_frames` does not continuously grow
- AI sampling remains around configured 3 FPS
- `ai_dropped_stale_frames` does not continuously explode
- no ghost session/resource leak is introduced

---

## 5. Suggested file scope

Expected mobile writes may include:

- `mobile/lib/core/config/app_config.dart`
- `mobile/lib/features/streaming/camera_frame_service.dart`
- `mobile/lib/features/streaming/native_jpeg_encoder.dart`
- `mobile/lib/features/streaming/streaming_controller.dart`
- `mobile/lib/features/streaming/streaming_state.dart` only if accepted-FPS telemetry needs it
- `mobile/android/app/src/main/kotlin/**/MainActivity.kt`
- focused tests under `mobile/test/features/streaming/**`

Only touch `camera_page.dart` if a small telemetry label is genuinely needed.

Gateway and AI code are READ-ONLY for this task unless a concrete incompatibility is proven. Do not “fix” performance by changing Gateway AI sampling or AI model input settings.

---

## 6. Tests required before real-device acceptance

### Automated

Run:

```bash
cd mobile
flutter analyze
flutter test
```

Add focused tests for pure sizing/profile logic where practical.

At minimum verify:

- no upscale above source size
- aspect ratio preserved
- output dimensions valid/even
- 720-wide source does not collapse to ~360 when target is 640
- fallback profile never returns to 160/128
- accepted-FPS metric counts successful uploads, not encode completions

If native Android code is changed, compile/run the Android app; do not rely only on Dart unit tests.

### Real-device

Use the same real phone/network setup that reproduced the 160px frame.

Enable diagnostics for the test build.

Run at least these profiles if needed:

```text
A: medium + 640 + quality 65
B: medium + 640 + quality 60
C: medium + 480 + quality 60   (fallback only)
```

For each profile collect approximately 30–60 seconds of:

- actual source WxH
- actual encoded WxH
- average JPEG bytes
- encode ms
- upload ms
- Gateway-accepted FPS
- mobile dropped/failed counts
- Gateway queued_frames
- Gateway ai_sampled_frames / ai_dispatched_frames delta
- a saved/observed Gateway JPEG visual check
- AI detection smoke result on a representative welding/PPE scene

---

## 7. Acceptance criteria — do not mark DONE without these

All must pass:

- [ ] Gateway frame is no longer ~160px-wide.
- [ ] Preferred output is approximately 640-class resolution; 480-class is allowed only if needed to sustain performance.
- [ ] JPEG visual quality preserves PPE detail materially better than the current frame.
- [ ] Real phone sustains **>=5 successful Gateway-accepted FPS** for at least 60 seconds.
- [ ] Target 8–15 accepted FPS is used when the device can sustain it.
- [ ] `queued_frames` does not trend upward continuously.
- [ ] Gateway still samples AI at approximately its configured 3 FPS.
- [ ] AI receives the improved JPEG unchanged by a new destructive mobile/Gateway resize.
- [ ] Representative AI smoke test produces sensible detections.
- [ ] Camera preview remains responsive.
- [ ] Start/stop/reconnect/background lifecycle still works.
- [ ] `flutter analyze` PASS.
- [ ] `flutter test` PASS.
- [ ] No Backend/Gateway/AI contract was invented or silently changed.

---

## 8. STOP conditions

Stop and report before broadening scope if:

1. `ResolutionPreset.medium/high` does not produce the expected source size on the actual device.
2. Android native YUV plane layout differs from assumptions and produces corrupted chroma/geometry.
3. 640/480-class JPEG exceeds Gateway's configured frame-size limit in normal operation.
4. Network bandwidth rather than encode CPU is proven to be the <5 FPS bottleneck.
5. AI Worker or Gateway performs another destructive resize not visible from the currently inspected code.
6. The only way to pass would require changing recorder, AI model, Backend contracts, or session architecture.

Do not hide a STOP condition by lowering resolution back to 160.

---

## 9. Final report format

When finished, report exactly:

```text
ROOT CAUSE:
- ...

CHANGED:
- file: change

FINAL PROFILE:
- camera source preset:
- observed source WxH:
- encoded WxH:
- JPEG quality:
- average JPEG bytes:
- target/paced FPS:
- accepted FPS:

PERFORMANCE:
- encode avg/p95 ms:
- upload avg/p95 ms:
- mobile dropped/failed:
- Gateway queued_frames behavior:
- Gateway AI sampled/dispatched rate:

AI SMOKE:
- representative frame:
- detection result:

TESTS:
- flutter analyze:
- flutter test:
- Android build/device:
- 60s real-device acceptance:

REGRESSION:
- preview:
- start/stop:
- reconnect:
- background/resume:
- session cleanup:

RISKS / FOLLOW-UP:
- ...
```

---

## 10. First Cursor instruction

Do **not** edit immediately.

First reply with:

1. current root-cause confirmation from the actual branch;
2. exact source camera resolution path;
3. exact current encoded resolution calculation;
4. whether `sendFps` currently measures encode completion or HTTP-accepted frames;
5. proposed files to change;
6. proposed real-device profile matrix;
7. any STOP condition.

Only after approval implement the smallest safe fix.
