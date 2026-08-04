from fastapi import APIRouter, Depends, HTTPException, Response, status

from app.api.dependencies import (
    get_camera_session_lifecycle_notifier,
    get_camera_session_validator,
    get_session_manager,
)
from app.api.schemas.session import (
    OpenSessionRequest,
    OpenSessionResponse,
    SessionActionRequest,
    SessionResponse,
)
from app.domain.session import CameraSessionContext
from app.services.session_manager import (
    SessionConflictError,
    SessionManager,
    SessionNotFoundError,
)
from app.services.session_validator import CameraSessionValidator

from app.services.session_lifecycle_notifier import (
    CameraSessionLifecycleNotifier,
)

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
        session_lifecycle_notifier: CameraSessionLifecycleNotifier = Depends(
            get_camera_session_lifecycle_notifier,
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

    if created:
        await session_lifecycle_notifier.notify_open(
            camera_id=session.camera_id,
            session_id=session.session_id,
            opened_at=session.opened_at,
        )

    response.status_code = (
        status.HTTP_201_CREATED
        if created
        else status.HTTP_200_OK
    )

    return OpenSessionResponse(
        created=created,
        session=_to_session_response(session),
    )
@router.post(
    "/{session_id}/heartbeat",
    response_model=SessionResponse,
    responses={
        status.HTTP_404_NOT_FOUND: {
            "description": "Active session was not found",
        },
        status.HTTP_409_CONFLICT: {
            "description": "Session belongs to another camera",
        },
    },
)
async def heartbeat_session(
        session_id: str,
        request: SessionActionRequest,
        session_manager: SessionManager = Depends(get_session_manager),
        session_lifecycle_notifier: CameraSessionLifecycleNotifier = Depends(
            get_camera_session_lifecycle_notifier,
        ),
) -> SessionResponse:
    try:
        session = await session_manager.heartbeat(
            camera_id=request.camera_id,
            session_id=session_id,
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

    await session_lifecycle_notifier.notify_heartbeat(
        camera_id=session.camera_id,
        session_id=session.session_id,
        heartbeat_at=session.last_heartbeat_at,
    )

    return _to_session_response(session)

@router.post(
    "/{session_id}/close",
    status_code=status.HTTP_204_NO_CONTENT,
    responses={
        status.HTTP_409_CONFLICT: {
            "description": "Session belongs to another camera",
        },
    },
)
async def close_session(
        session_id: str,
        request: SessionActionRequest,
        session_manager: SessionManager = Depends(get_session_manager),
        session_lifecycle_notifier: CameraSessionLifecycleNotifier = Depends(
            get_camera_session_lifecycle_notifier,
        ),
) -> Response:
    try:
        closed_session = await session_manager.close_session(
            camera_id=request.camera_id,
            session_id=session_id,
        )
    except SessionConflictError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="SESSION_CONFLICT",
        ) from exc

    if closed_session is None:
        return Response(
            status_code=status.HTTP_204_NO_CONTENT,
        )

    closed_at = closed_session.closed_at

    if closed_at is None:
        raise RuntimeError(
            "Closed session does not have a closed_at timestamp"
        )

    await session_lifecycle_notifier.notify_close(
        camera_id=closed_session.camera_id,
        session_id=closed_session.session_id,
        closed_at=closed_at,
    )

    return Response(
        status_code=status.HTTP_204_NO_CONTENT,
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