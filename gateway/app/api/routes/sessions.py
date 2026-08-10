from fastapi import APIRouter, Depends, HTTPException, Response, status

from app.api.dependencies import (
    get_camera_session_lifecycle_notifier,
    get_camera_session_validator,
    get_session_frame_ingestion_worker_coordinator,
    get_session_frame_queue_manager,
    get_session_frame_ring_buffer_manager,
    get_session_manager,
)
from app.api.schemas.session import (
    OpenSessionRequest,
    OpenSessionResponse,
    SessionActionRequest,
    SessionResponse,
)
from app.domain.session import CameraSessionContext
from app.services.session_frame_queue_manager import (
    FrameQueueConflictError,
    SessionFrameQueueManager,
)
from app.services.session_frame_ingestion_worker import (
    SessionFrameIngestionWorkerConflictError,
    SessionFrameIngestionWorkerCoordinator,
)
from app.services.session_frame_ring_buffer import (
    FrameRingBufferConflictError,
)
from app.services.session_frame_ring_buffer_manager import (
    SessionFrameRingBufferManager,
)
from app.services.session_lifecycle_notifier import (
    CameraSessionLifecycleNotifier,
)
from app.services.session_manager import (
    SessionConflictError,
    SessionManager,
    SessionNotFoundError,
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
        session_lifecycle_notifier: CameraSessionLifecycleNotifier = Depends(
            get_camera_session_lifecycle_notifier,
        ),
        session_frame_queue_manager: SessionFrameQueueManager = Depends(
            get_session_frame_queue_manager,
        ),
        session_frame_ring_buffer_manager: (
            SessionFrameRingBufferManager
        ) = Depends(
            get_session_frame_ring_buffer_manager,
        ),
        ingestion_worker_coordinator: (
            SessionFrameIngestionWorkerCoordinator
        ) = Depends(
            get_session_frame_ingestion_worker_coordinator,
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

    queue_created = False
    buffer_created = False
    worker_started = False

    try:
        queue, queue_created = await session_frame_queue_manager.open_queue(
            camera_id=session.camera_id,
            session_id=session.session_id,
        )

        _, buffer_created = (
            await session_frame_ring_buffer_manager.open_buffer(
                camera_id=session.camera_id,
                session_id=session.session_id,
            )
        )

        worker_started = await ingestion_worker_coordinator.start_worker(
            camera_id=session.camera_id,
            session_id=session.session_id,
            queue=queue,
            ring_buffer_manager=session_frame_ring_buffer_manager,
        )
    except (
            FrameQueueConflictError,
            FrameRingBufferConflictError,
            SessionFrameIngestionWorkerConflictError,
    ) as exc:
        await _rollback_open_session_resources(
            camera_id=session.camera_id,
            session_id=session.session_id,
            session_created=created,
            queue_created=queue_created,
            buffer_created=buffer_created,
            worker_started=worker_started,
            session_manager=session_manager,
            session_frame_queue_manager=session_frame_queue_manager,
            session_frame_ring_buffer_manager=(
                session_frame_ring_buffer_manager
            ),
            ingestion_worker_coordinator=(
                ingestion_worker_coordinator
            ),
        )

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
        session_frame_queue_manager: SessionFrameQueueManager = Depends(
            get_session_frame_queue_manager,
        ),
        session_frame_ring_buffer_manager: (
            SessionFrameRingBufferManager
        ) = Depends(
            get_session_frame_ring_buffer_manager,
        ),
        ingestion_worker_coordinator: (
            SessionFrameIngestionWorkerCoordinator
        ) = Depends(
            get_session_frame_ingestion_worker_coordinator,
        ),
) -> Response:
    try:
        session = await session_manager.get_session(session_id)
    except SessionNotFoundError:
        session = None

    if (
            session is not None
            and session.camera_id != request.camera_id
    ):
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="SESSION_CONFLICT",
        )

    try:
        await ingestion_worker_coordinator.stop_worker(
            camera_id=request.camera_id,
            session_id=session_id,
        )

        await session_frame_queue_manager.close_queue(
            camera_id=request.camera_id,
            session_id=session_id,
        )

        await session_frame_ring_buffer_manager.close_buffer(
            camera_id=request.camera_id,
            session_id=session_id,
        )

        closed_session = await session_manager.close_session(
            camera_id=request.camera_id,
            session_id=session_id,
        )
    except (
            SessionConflictError,
            FrameQueueConflictError,
            FrameRingBufferConflictError,
            SessionFrameIngestionWorkerConflictError,
    ) as exc:
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


async def _rollback_open_session_resources(
        camera_id: str,
        session_id: str,
        session_created: bool,
        queue_created: bool,
        buffer_created: bool,
        worker_started: bool,
        session_manager: SessionManager,
        session_frame_queue_manager: SessionFrameQueueManager,
        session_frame_ring_buffer_manager: SessionFrameRingBufferManager,
        ingestion_worker_coordinator: (
            SessionFrameIngestionWorkerCoordinator
        ),
) -> None:
    if worker_started:
        await ingestion_worker_coordinator.stop_worker(
            camera_id=camera_id,
            session_id=session_id,
        )

    if queue_created:
        await session_frame_queue_manager.close_queue(
            camera_id=camera_id,
            session_id=session_id,
        )

    if buffer_created:
        await session_frame_ring_buffer_manager.close_buffer(
            camera_id=camera_id,
            session_id=session_id,
        )

    if session_created:
        await session_manager.close_session(
            camera_id=camera_id,
            session_id=session_id,
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
