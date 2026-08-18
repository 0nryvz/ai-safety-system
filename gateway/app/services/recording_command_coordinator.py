import asyncio
from dataclasses import dataclass

from app.api.schemas.recording import (
    StartRecordingCommandRequest,
    StopRecordingCommandRequest,
)
from app.services.event_recorder import (
    EventRecorderCoordinator,
    EventRecordingConflictError,
    EventRecordingNotFoundError,
)
from app.services.session_frame_ring_buffer_manager import (
    SessionFrameRingBufferManager,
)
from app.services.session_manager import (
    SessionConflictError,
    SessionManager,
)


class RecordingStartConflictError(RuntimeError):
    """Aynı ihlal için ikinci aktif start komutu farklı commandId ile geldi."""


class RecordingStopConflictError(RuntimeError):
    """Aynı ihlal için ikinci stop komutu farklı commandId ile geldi."""


class RecordingStartMissingError(LookupError):
    """Stop komutu için ihlal bazında daha önce accepted start kaydı yok."""


@dataclass(frozen=True)
class RecordingCommandAck:
    command_id: str
    violation_id: str
    idempotent: bool


@dataclass(slots=True)
class RecordingCommandState:
    violation_id: str
    recording_id: str
    camera_id: str
    session_id: str
    start_command_id: str
    stop_command_id: str | None = None


class RecordingCommandCoordinator:
    def __init__(
            self,
            event_recorder_coordinator: EventRecorderCoordinator,
            ring_buffer_manager: SessionFrameRingBufferManager,
    ) -> None:
        self._event_recorder_coordinator = (
            event_recorder_coordinator
        )

        self._ring_buffer_manager = (
            ring_buffer_manager
        )

        self._states_by_violation: dict[
            str,
            RecordingCommandState,
        ] = {}

        self._lock = asyncio.Lock()

    async def accept_start(
            self,
            command: StartRecordingCommandRequest,
            session_manager: SessionManager,
    ) -> RecordingCommandAck:
        await self._validate_session_ownership(
            session_manager=session_manager,
            session_id=command.session_id,
            camera_id=command.camera_id,
        )

        async with self._lock:
            existing_state = (
                self._states_by_violation.get(
                    command.violation_id
                )
            )

            if existing_state is not None:
                if (
                        existing_state.start_command_id
                        == command.command_id
                ):
                    if (
                            existing_state.recording_id
                            != command.recording_id
                    ):
                        raise RecordingStartConflictError(
                            f"Violation '{command.violation_id}' "
                            "already accepted with a different recording identity"
                        )

                    return RecordingCommandAck(
                        command_id=command.command_id,
                        violation_id=command.violation_id,
                        idempotent=True,
                    )

                raise RecordingStartConflictError(
                    f"Violation '{command.violation_id}' "
                    "already has an active recording start"
                )

            try:
                await (
                    self._event_recorder_coordinator
                    .start_recording(
                        recording_id=(
                            command.recording_id
                        ),
                        violation_id=(
                            command.violation_id
                        ),
                        camera_id=(
                            command.camera_id
                        ),
                        session_id=(
                            command.session_id
                        ),
                        started_at=(
                            command.started_at
                        ),
                        pre_buffer_seconds=(
                            command.pre_buffer_seconds
                        ),
                        post_buffer_seconds=(
                            command.post_buffer_seconds
                        ),
                        max_clip_seconds=(
                            command.max_clip_seconds
                        ),
                        ring_buffer_manager=(
                            self._ring_buffer_manager
                        ),
                    )
                )

            except EventRecordingConflictError as exc:
                raise RecordingStartConflictError(
                    f"Violation '{command.violation_id}' "
                    "already has an active event recorder"
                ) from exc

            self._states_by_violation[
                command.violation_id
            ] = RecordingCommandState(
                violation_id=(
                    command.violation_id
                ),
                recording_id=(
                    command.recording_id
                ),
                camera_id=(
                    command.camera_id
                ),
                session_id=(
                    command.session_id
                ),
                start_command_id=(
                    command.command_id
                ),
            )

            return RecordingCommandAck(
                command_id=command.command_id,
                violation_id=command.violation_id,
                idempotent=False,
            )

    async def accept_stop(
            self,
            command: StopRecordingCommandRequest,
    ) -> RecordingCommandAck:
        async with self._lock:
            existing_state = (
                self._states_by_violation.get(
                    command.violation_id
                )
            )

            if existing_state is None:
                raise RecordingStartMissingError(
                    f"Start command not found for "
                    f"violation '{command.violation_id}'"
                )

            if (
                    existing_state.stop_command_id
                    is not None
            ):
                if (
                        existing_state.stop_command_id
                        == command.command_id
                ):
                    return RecordingCommandAck(
                        command_id=command.command_id,
                        violation_id=command.violation_id,
                        idempotent=True,
                    )

                raise RecordingStopConflictError(
                    f"Stop command already accepted for "
                    f"violation '{command.violation_id}'"
                )

            try:
                await (
                    self._event_recorder_coordinator
                    .request_stop(
                        violation_id=(
                            command.violation_id
                        ),
                        ended_at=(
                            command.ended_at
                        ),
                    )
                )

            except EventRecordingNotFoundError as exc:
                raise RecordingStartMissingError(
                    f"Event recorder start not found for "
                    f"violation '{command.violation_id}'"
                ) from exc

            existing_state.stop_command_id = (
                command.command_id
            )

            return RecordingCommandAck(
                command_id=command.command_id,
                violation_id=command.violation_id,
                idempotent=False,
            )

    @staticmethod
    async def _validate_session_ownership(
            session_manager: SessionManager,
            session_id: str,
            camera_id: str,
    ) -> None:
        session = await (
            session_manager.get_session(
                session_id
            )
        )

        if session.camera_id != camera_id:
            raise SessionConflictError(
                f"Session '{session_id}' "
                "belongs to another camera"
            )