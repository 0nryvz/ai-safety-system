from fastapi import APIRouter, Depends, HTTPException, status

from app.api.dependencies import (
    get_recording_command_coordinator,
    get_session_manager,
)
from app.api.schemas.recording import (
    RecordingCommandAckResponse,
    StartRecordingCommandRequest,
    StopRecordingCommandRequest,
)
from app.services.recording_command_coordinator import (
    RecordingCommandCoordinator,
    RecordingStartConflictError,
    RecordingStartMissingError,
    RecordingStopConflictError,
)
from app.services.session_manager import (
    SessionConflictError,
    SessionManager,
    SessionNotFoundError,
)


router = APIRouter(
    prefix="/internal/v1/recordings",
    tags=["Internal Recordings"],
)


@router.post(
    "/commands/start",
    response_model=RecordingCommandAckResponse,
    status_code=status.HTTP_202_ACCEPTED,
    responses={
        status.HTTP_404_NOT_FOUND: {"description": "Session not found"},
        status.HTTP_409_CONFLICT: {
            "description": "Session ownership conflict or active start conflict",
        },
    },
)
async def accept_start_recording_command(
        request: StartRecordingCommandRequest,
        session_manager: SessionManager = Depends(get_session_manager),
        coordinator: RecordingCommandCoordinator = Depends(
            get_recording_command_coordinator,
        ),
) -> RecordingCommandAckResponse:
    try:
        ack = await coordinator.accept_start(
            command=request,
            session_manager=session_manager,
        )
    except SessionNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="SESSION_NOT_FOUND",
        ) from exc
    except SessionConflictError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="SESSION_CONFLICT",
        ) from exc
    except RecordingStartConflictError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="RECORDING_START_CONFLICT",
        ) from exc

    return RecordingCommandAckResponse(
        command_id=ack.command_id,
        violation_id=ack.violation_id,
        idempotent=ack.idempotent,
    )


@router.post(
    "/commands/stop",
    response_model=RecordingCommandAckResponse,
    status_code=status.HTTP_202_ACCEPTED,
    responses={
        status.HTTP_404_NOT_FOUND: {"description": "No prior start for violation"},
        status.HTTP_409_CONFLICT: {"description": "Stop conflict"},
    },
)
async def accept_stop_recording_command(
        request: StopRecordingCommandRequest,
        coordinator: RecordingCommandCoordinator = Depends(
            get_recording_command_coordinator,
        ),
) -> RecordingCommandAckResponse:
    try:
        ack = await coordinator.accept_stop(command=request)
    except RecordingStartMissingError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="RECORDING_NOT_FOUND_FOR_VIOLATION",
        ) from exc
    except RecordingStopConflictError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="RECORDING_STOP_CONFLICT",
        ) from exc

    return RecordingCommandAckResponse(
        command_id=ack.command_id,
        violation_id=ack.violation_id,
        idempotent=ack.idempotent,
    )
