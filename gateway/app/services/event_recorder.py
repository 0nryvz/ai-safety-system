import asyncio
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from enum import StrEnum

from app.domain.frame import FramePacket
from app.services.video_encoder import (
    VideoEncoder,
    VideoEncodingResult,
)
from app.services.session_frame_ring_buffer_manager import (
    FrameRingBufferNotFoundError,
    SessionFrameRingBufferManager,
)


class EventRecordingConflictError(RuntimeError):
    """A recording already exists for the same violation."""


class EventRecordingNotFoundError(LookupError):
    """Recording context could not be found."""


class EventRecordingStatus(StrEnum):
    RECORDING = "RECORDING"
    POST_ROLL = "POST_ROLL"
    FINALIZING = "FINALIZING"
    READY = "READY"
    ERROR = "ERROR"


@dataclass(frozen=True, slots=True)
class EventRecordingSnapshot:
    violation_id: str
    recording_id: str
    camera_id: str
    session_id: str

    status: EventRecordingStatus

    frame_count: int

    clip_started_at: datetime
    hard_deadline_at: datetime

    stop_requested_at: datetime | None
    finalize_after_at: datetime | None

    output_path: str | None
    duration_ms: int | None
    size_bytes: int | None

    error: str | None


@dataclass(slots=True)
class _EventRecordingContext:
    violation_id: str
    recording_id: str
    camera_id: str
    session_id: str

    started_at: datetime

    pre_buffer_seconds: int
    post_buffer_seconds: int
    max_clip_seconds: int

    clip_started_at: datetime
    hard_deadline_at: datetime

    frames: list[FramePacket] = field(
        default_factory=list,
    )

    status: EventRecordingStatus = (
        EventRecordingStatus.RECORDING
    )

    stop_requested_at: datetime | None = None
    finalize_after_at: datetime | None = None

    result: VideoEncodingResult | None = None

    error: str | None = None

    finalized_frame_count: int = 0

    post_roll_timer_task: asyncio.Task[None] | None = None

    finalization_task: asyncio.Task[None] | None = None


class EventRecorderCoordinator:
    def __init__(
            self,
            video_encoder: VideoEncoder,
    ) -> None:
        self._video_encoder = video_encoder

        self._contexts_by_violation: dict[
            str,
            _EventRecordingContext,
        ] = {}

        self._active_violations_by_session: dict[
            str,
            set[str],
        ] = {}

        self._lock = asyncio.Lock()

    async def start_recording(
            self,
            *,
            recording_id: str,
            violation_id: str,
            camera_id: str,
            session_id: str,
            started_at: datetime,
            pre_buffer_seconds: int,
            post_buffer_seconds: int,
            max_clip_seconds: int,
            ring_buffer_manager: SessionFrameRingBufferManager,
    ) -> EventRecordingSnapshot:

        clip_started_at = (
                started_at
                - timedelta(
            seconds=pre_buffer_seconds,
        )
        )

        hard_deadline_at = (
                clip_started_at
                + timedelta(
            seconds=max_clip_seconds,
        )
        )

        async with self._lock:
            if (
                    violation_id
                    in self._contexts_by_violation
            ):
                raise EventRecordingConflictError(
                    "Recording already exists "
                    f"for violation '{violation_id}'"
                )

            context = _EventRecordingContext(
                violation_id=violation_id,
                recording_id=recording_id,
                camera_id=camera_id,
                session_id=session_id,
                started_at=started_at,
                pre_buffer_seconds=pre_buffer_seconds,
                post_buffer_seconds=post_buffer_seconds,
                max_clip_seconds=max_clip_seconds,
                clip_started_at=clip_started_at,
                hard_deadline_at=hard_deadline_at,
            )

            self._contexts_by_violation[
                violation_id
            ] = context

            self._active_violations_by_session \
                .setdefault(
                session_id,
                set(),
            ) \
                .add(
                violation_id
            )

        try:
            buffered_frames = (
                await ring_buffer_manager.snapshot(
                    camera_id=camera_id,
                    session_id=session_id,
                )
            )

        except FrameRingBufferNotFoundError:
            # Ring buffer yoksa recording'i
            # tamamen iptal etmiyoruz.
            # Sadece pre-buffer olmadan devam ediyoruz.
            buffered_frames = ()

        except Exception:
            async with self._lock:
                self._contexts_by_violation.pop(
                    violation_id,
                    None,
                )

                violation_ids = (
                    self
                    ._active_violations_by_session
                    .get(
                        session_id
                    )
                )

                if violation_ids is not None:
                    violation_ids.discard(
                        violation_id
                    )

                    if not violation_ids:
                        self._active_violations_by_session.pop(
                            session_id,
                            None,
                        )

            raise

        async with self._lock:
            current = (
                self._contexts_by_violation.get(
                    violation_id
                )
            )

            if current is None:
                raise EventRecordingNotFoundError(
                    "Recording disappeared "
                    "during start: "
                    f"'{violation_id}'"
                )

            filtered_frames = [
                frame
                for frame in buffered_frames
                if (
                        frame.camera_id
                        == camera_id
                        and frame.session_id
                        == session_id
                        and frame.captured_at
                        >= clip_started_at
                        and frame.captured_at
                        <= hard_deadline_at
                )
            ]

            filtered_frames.sort(
                key=lambda frame: frame.captured_at
            )

            current.frames.extend(
                filtered_frames
            )

            current.frames.sort(
                key=lambda frame: frame.captured_at
            )

            return self._create_snapshot(
                current
            )

    async def offer_frame(
            self,
            frame: FramePacket,
    ) -> None:
        async with self._lock:
            violation_ids = tuple(
                self._active_violations_by_session.get(
                    frame.session_id,
                    (),
                )
            )

            for violation_id in violation_ids:
                context = (
                    self._contexts_by_violation.get(
                        violation_id
                    )
                )

                if context is None:
                    continue

                if context.camera_id != frame.camera_id:
                    continue

                if context.status not in {
                    EventRecordingStatus.RECORDING,
                    EventRecordingStatus.POST_ROLL,
                }:
                    continue

                if (
                        frame.captured_at
                        < context.clip_started_at
                ):
                    continue

                if (
                        context.frames
                        and frame.captured_at
                        <= context.frames[-1].captured_at
                ):
                    # Ring buffer snapshot ile
                    # aynı frame live akıştan tekrar
                    # gelirse duplicate ekleme.
                    continue

                if (
                        frame.captured_at
                        > context.hard_deadline_at
                ):
                    self._begin_finalize_locked(
                        context
                    )
                    continue

                context.frames.append(
                    frame
                )

                reached_hard_deadline = (
                        frame.captured_at
                        >= context.hard_deadline_at
                )

                reached_post_roll = (
                        context.finalize_after_at
                        is not None
                        and (
                                frame.captured_at
                                >= context.finalize_after_at
                        )
                )

                if (
                        reached_hard_deadline
                        or reached_post_roll
                ):
                    self._begin_finalize_locked(
                        context
                    )

    async def request_stop(
            self,
            *,
            violation_id: str,
            ended_at: datetime,
    ) -> EventRecordingSnapshot:
        async with self._lock:
            context = (
                self._contexts_by_violation.get(
                    violation_id
                )
            )

            if context is None:
                raise EventRecordingNotFoundError(
                    "Recording not found for "
                    f"violation '{violation_id}'"
                )

            if context.status in {
                EventRecordingStatus.FINALIZING,
                EventRecordingStatus.READY,
                EventRecordingStatus.ERROR,
            }:
                return self._create_snapshot(
                    context
                )

            if context.stop_requested_at is None:
                context.stop_requested_at = (
                    ended_at
                )

                context.status = (
                    EventRecordingStatus.POST_ROLL
                )

                requested_finalize_at = (
                        ended_at
                        + timedelta(
                    seconds=(
                        context.post_buffer_seconds
                    ),
                )
                )

                context.finalize_after_at = min(
                    requested_finalize_at,
                    context.hard_deadline_at,
                )

            if (
                    context.frames
                    and context.finalize_after_at is not None
                    and (
                    context.frames[-1].captured_at
                    >= context.finalize_after_at
            )
            ):
                self._begin_finalize_locked(
                    context
                )

                return self._create_snapshot(
                    context
                )

            if context.finalize_after_at is None:
                self._begin_finalize_locked(
                    context
                )

                return self._create_snapshot(
                    context
                )

            target_time = (
                context.finalize_after_at
            )

            if target_time.tzinfo is not None:
                now = datetime.now(
                    target_time.tzinfo
                )
            else:
                now = datetime.now()

            delay_seconds = max(
                0.0,
                (
                        target_time
                        - now
                ).total_seconds(),
            )

            if delay_seconds == 0:
                self._begin_finalize_locked(
                    context
                )

            elif (
                    context.post_roll_timer_task
                    is None
                    or context.post_roll_timer_task.done()
            ):
                context.post_roll_timer_task = (
                    asyncio.create_task(
                        self._finalize_after_delay(
                            violation_id=(
                                violation_id
                            ),
                            delay_seconds=(
                                delay_seconds
                            ),
                        )
                    )
                )

            return self._create_snapshot(
                context
            )
    async def _finalize_after_delay(
            self,
            *,
            violation_id: str,
            delay_seconds: float,
    ) -> None:
        try:
            await asyncio.sleep(
                delay_seconds
            )

        except asyncio.CancelledError:
            return

        async with self._lock:
            context = (
                self._contexts_by_violation.get(
                    violation_id
                )
            )

            if context is None:
                return

            self._begin_finalize_locked(
                context
            )

    def _begin_finalize_locked(
            self,
            context: _EventRecordingContext,
    ) -> asyncio.Task[None] | None:

        if context.status in {
            EventRecordingStatus.FINALIZING,
            EventRecordingStatus.READY,
            EventRecordingStatus.ERROR,
        }:
            return context.finalization_task

        current_task = asyncio.current_task()

        timer_task = (
            context.post_roll_timer_task
        )

        if (
                timer_task is not None
                and timer_task is not current_task
                and not timer_task.done()
        ):
            timer_task.cancel()

        context.status = (
            EventRecordingStatus.FINALIZING
        )

        self._remove_from_active_locked(
            context
        )

        frames = tuple(
            context.frames
        )

        task = asyncio.create_task(
            self._finalize_context(
                violation_id=context.violation_id,
                frames=frames,
            )
        )

        context.finalization_task = task

        return task

    async def _finalize_context(
            self,
            *,
            violation_id: str,
            frames: tuple[
                FramePacket,
                ...
            ],
    ) -> None:

        if not frames:
            async with self._lock:
                context = (
                    self._contexts_by_violation.get(
                        violation_id
                    )
                )

                if context is None:
                    return

                context.status = (
                    EventRecordingStatus.ERROR
                )

                context.error = (
                    "NO_FRAMES_CAPTURED"
                )

                context.finalized_frame_count = 0

                context.frames.clear()

            return

        try:
            result = await self._video_encoder.encode(
                violation_id=violation_id,
                frames=frames,
            )

        except asyncio.CancelledError:
            raise

        except Exception as exc:
            async with self._lock:
                context = (
                    self._contexts_by_violation.get(
                        violation_id
                    )
                )

                if context is None:
                    return

                context.status = (
                    EventRecordingStatus.ERROR
                )

                context.error = (
                    f"{type(exc).__name__}: "
                    f"{exc}"
                )

                context.finalized_frame_count = (
                    len(frames)
                )

                context.frames.clear()

            return

        async with self._lock:
            context = (
                self._contexts_by_violation.get(
                    violation_id
                )
            )

            if context is None:
                return

            context.status = (
                EventRecordingStatus.READY
            )

            context.result = result

            context.error = None

            context.finalized_frame_count = (
                len(frames)
            )

            context.frames.clear()

    async def get_snapshot(
            self,
            violation_id: str,
    ) -> EventRecordingSnapshot:
        async with self._lock:
            context = (
                self._contexts_by_violation.get(
                    violation_id
                )
            )

            if context is None:
                raise EventRecordingNotFoundError(
                    "Recording not found for "
                    f"violation '{violation_id}'"
                )

            return self._create_snapshot(
                context
            )

    async def wait_until_finalized(
            self,
            violation_id: str,
    ) -> EventRecordingSnapshot:

        while True:
            async with self._lock:
                context = (
                    self._contexts_by_violation.get(
                        violation_id
                    )
                )

                if context is None:
                    raise EventRecordingNotFoundError(
                        "Recording not found for "
                        f"violation '{violation_id}'"
                    )

                if context.status in {
                    EventRecordingStatus.READY,
                    EventRecordingStatus.ERROR,
                }:
                    return self._create_snapshot(
                        context
                    )

                task = (
                    context.finalization_task
                )

            if task is None:
                await asyncio.sleep(0.01)
                continue

            try:
                await task
            except asyncio.CancelledError:
                raise

    async def finalize_session(
            self,
            *,
            camera_id: str,
            session_id: str,
    ) -> int:
        tasks: list[
            asyncio.Task[None]
        ] = []

        async with self._lock:
            violation_ids = tuple(
                self._active_violations_by_session.get(
                    session_id,
                    (),
                )
            )

            for violation_id in violation_ids:
                context = (
                    self._contexts_by_violation.get(
                        violation_id
                    )
                )

                if context is None:
                    continue

                if context.camera_id != camera_id:
                    continue

                task = self._begin_finalize_locked(
                    context
                )

                if task is not None:
                    tasks.append(
                        task
                    )

        if tasks:
            await asyncio.gather(
                *tasks,
                return_exceptions=True,
            )

        return len(tasks)

    async def clear(
            self,
    ) -> int:
        async with self._lock:
            contexts = tuple(
                self._contexts_by_violation.values()
            )

            self._contexts_by_violation.clear()
            self._active_violations_by_session.clear()

        tasks: list[
            asyncio.Task[None]
        ] = []

        for context in contexts:
            for task in (
                    context.post_roll_timer_task,
                    context.finalization_task,
            ):
                if (
                        task is not None
                        and not task.done()
                ):
                    task.cancel()
                    tasks.append(
                        task
                    )

        if tasks:
            await asyncio.gather(
                *tasks,
                return_exceptions=True,
            )

        return len(contexts)

    def _remove_from_active_locked(
            self,
            context: _EventRecordingContext,
    ) -> None:

        violation_ids = (
            self._active_violations_by_session.get(
                context.session_id
            )
        )

        if violation_ids is None:
            return

        violation_ids.discard(
            context.violation_id
        )

        if not violation_ids:
            self._active_violations_by_session.pop(
                context.session_id,
                None,
            )

    @staticmethod
    def _create_snapshot(
            context: _EventRecordingContext,
    ) -> EventRecordingSnapshot:
        result = context.result

        return EventRecordingSnapshot(
            violation_id=context.violation_id,
            recording_id=context.recording_id,
            camera_id=context.camera_id,
            session_id=context.session_id,
            status=context.status,
            frame_count=(
                context.finalized_frame_count
                if context.status in {
                    EventRecordingStatus.READY,
                    EventRecordingStatus.ERROR,
                }
                else len(context.frames)
            ),
            clip_started_at=(
                context.clip_started_at
            ),
            hard_deadline_at=(
                context.hard_deadline_at
            ),
            stop_requested_at=(
                context.stop_requested_at
            ),
            finalize_after_at=(
                context.finalize_after_at
            ),
            output_path=(
                str(result.output_path)
                if result is not None
                else None
            ),
            duration_ms=(
                result.duration_ms
                if result is not None
                else None
            ),
            size_bytes=(
                result.size_bytes
                if result is not None
                else None
            ),
            error=context.error,
        )