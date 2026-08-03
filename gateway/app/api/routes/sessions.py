from fastapi import APIRouter, Depends, HTTPException, Response, status

from app.api.dependencies import (
    get_camera_session_validator,
    get_session_manager,
)
from app.api.schemas.session import (
    OpenSessionRequest,
    OpenSessionResponse,
    SessionResponse,
)
from app.domain.session import CameraSessionContext
from app.services.session_manager import (
    SessionConflictError,
    SessionManager,
)
from app.services.session_validator import CameraSessionValidator


router = APIRouter(
    prefix="/api/v1/sessions",
    tags=["Sessions"],
)


@router.post(
    "/open",
    response_model=OpenSessionResponse,
    status_code=status.HTTP_201_CREATED,
    responses={
        status.HTTP_200_OK: {
            "description": "Existing session reconnected",
        },
        status.HTTP_401_UNAUTHORIZED: {
            "description": "Invalid session token",
        },
        status.HTTP_403_FORBIDDEN: {
            "description": "Camera is inactive or session was rejected",
        },
        status.HTTP_409_CONFLICT: {
            "description": "Session ID belongs to another camera",
        },
    },
)
async def open_session(
        request: OpenSessionRequest,
        response: Response,
        session_manager: SessionManager = Depends(get_session_manager),
        session_validator: CameraSessionValidator = Depends(
            get_camera_session_validator,
        ),
) -> OpenSessionResponse:
    validation_result = await session_validator.validate_open_session(
        camera_id=request.camera_id,
        session_id=request.session_id,
        session_token=request.session_token,
    )

    if not validation_result.is_valid:
        if validation_result.reason == "INVALID_SESSION_TOKEN":
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="INVALID_SESSION_TOKEN",
            )

        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=validation_result.reason or "SESSION_REJECTED",
        )

    if not validation_result.camera_active:
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail=validation_result.reason or "CAMERA_INACTIVE",
        )

    try:
        session, created = await session_manager.open_session(
            camera_id=request.camera_id,
            session_id=request.session_id,
        )
    except SessionConflictError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="SESSION_CONFLICT",
        ) from exc

    response.status_code = (
        status.HTTP_201_CREATED
        if created
        else status.HTTP_200_OK
    )

    return OpenSessionResponse(
        created=created,
        session=_to_session_response(session),
    )


def _to_session_response(
        session: CameraSessionContext,
) -> SessionResponse:
    return SessionResponse(
        camera_id=session.camera_id,
        session_id=session.session_id,
        status=session.status,
        opened_at=session.opened_at,
        last_heartbeat_at=session.last_heartbeat_at,
        frame_count=session.frame_count,
        dropped_frame_count=session.dropped_frame_count,
    )