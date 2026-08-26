from datetime import datetime, timezone
from typing import Annotated

from fastapi import (
    APIRouter,
    Depends,
    Header,
    HTTPException,
    Request,
    status,
)

from app.api.dependencies import (
    get_session_frame_queue_manager,
    get_session_manager,
)
from app.api.schemas.frame import FrameUploadResponse
from app.core.config import Settings, get_settings
from app.domain.frame import FramePacket
from app.services.session_frame_queue_manager import (
    FrameQueueConflictError,
    FrameQueueNotFoundError,
    SessionFrameQueueManager,
)
from app.services.session_manager import (
    SessionConflictError,
    SessionManager,
    SessionNotFoundError,
)


router = APIRouter(
    prefix="/api/v1/sessions",
    tags=["Frames"],
)


@router.post(
    "/{session_id}/frames",
    response_model=FrameUploadResponse,
    status_code=status.HTTP_202_ACCEPTED,
    responses={
        status.HTTP_404_NOT_FOUND: {
            "description": "Active session was not found",
        },
        status.HTTP_409_CONFLICT: {
            "description": "Session belongs to another camera",
        },
        status.HTTP_413_REQUEST_ENTITY_TOO_LARGE: {
            "description": "Frame exceeds configured size limit",
        },
        status.HTTP_415_UNSUPPORTED_MEDIA_TYPE: {
            "description": "Only image/jpeg is accepted",
        },
        status.HTTP_422_UNPROCESSABLE_ENTITY: {
            "description": "Frame or metadata is invalid",
        },
    },
    openapi_extra={
        "requestBody": {
            "required": True,
            "content": {
                "image/jpeg": {
                    "schema": {
                        "type": "string",
                        "format": "binary",
                    }
                }
            },
        }
    },
)
async def upload_frame(
        session_id: str,
        request: Request,
        camera_id: Annotated[
            str,
            Header(
                alias="X-Camera-Id",
                min_length=1,
                max_length=128,
            ),
        ],
        frame_timestamp: Annotated[
            datetime,
            Header(alias="X-Frame-Timestamp"),
        ],
        session_manager: SessionManager = Depends(
            get_session_manager,
        ),
        session_frame_queue_manager: SessionFrameQueueManager = Depends(
            get_session_frame_queue_manager,
        ),
        settings: Settings = Depends(get_settings),
) -> FrameUploadResponse:
    media_type = (
        request.headers
        .get("content-type", "")
        .split(";", maxsplit=1)[0]
        .strip()
        .lower()
    )

    if media_type != "image/jpeg":
        raise HTTPException(
            status_code=status.HTTP_415_UNSUPPORTED_MEDIA_TYPE,
            detail="UNSUPPORTED_FRAME_CONTENT_TYPE",
        )

    if (
            frame_timestamp.tzinfo is None
            or frame_timestamp.utcoffset() is None
    ):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="FRAME_TIMESTAMP_MUST_INCLUDE_TIMEZONE",
        )

    try:
        await session_frame_queue_manager.get_queue(
            camera_id=camera_id,
            session_id=session_id,
        )
    except FrameQueueNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="SESSION_NOT_FOUND",
        ) from exc
    except FrameQueueConflictError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="SESSION_CONFLICT",
        ) from exc

    frame_data = await _read_limited_frame_body(
        request=request,
        max_bytes=settings.frame_max_bytes,
    )

    if not _looks_like_jpeg(frame_data):
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="INVALID_JPEG_FRAME",
        )

    captured_at = frame_timestamp.astimezone(timezone.utc)

    frame = FramePacket(
        camera_id=camera_id,
        session_id=session_id,
        captured_at=captured_at,
        content_type="image/jpeg",
        data=frame_data,
    )

    try:
        enqueue_result = (
            await session_frame_queue_manager.enqueue_frame(
                camera_id=camera_id,
                session_id=session_id,
                frame=frame,
            )
        )
    except FrameQueueNotFoundError as exc:
        raise HTTPException(
            status_code=status.HTTP_404_NOT_FOUND,
            detail="SESSION_NOT_FOUND",
        ) from exc
    except FrameQueueConflictError as exc:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="SESSION_CONFLICT",
        ) from exc

    try:
        session = await session_manager.register_frame(
            camera_id=camera_id,
            session_id=session_id,
        )

        if enqueue_result.dropped_frame is not None:
            session = await session_manager.register_dropped_frame(
                camera_id=camera_id,
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

    return FrameUploadResponse(
        accepted=True,
        camera_id=camera_id,
        session_id=session_id,
        captured_at=captured_at,
        size_bytes=frame.size_bytes,
        queue_depth=enqueue_result.queue_depth,
        queue_capacity=enqueue_result.queue_capacity,
        frame_count=session.frame_count,
        dropped_frame_count=session.dropped_frame_count,
    )


async def _read_limited_frame_body(
        request: Request,
        max_bytes: int,
) -> bytes:
    content_length = request.headers.get("content-length")

    if content_length is not None:
        try:
            declared_size = int(content_length)
        except ValueError:
            declared_size = 0

        if declared_size > max_bytes:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail="FRAME_TOO_LARGE",
            )

    chunks: list[bytes] = []
    received_size = 0

    async for chunk in request.stream():
        if not chunk:
            continue

        received_size += len(chunk)

        if received_size > max_bytes:
            raise HTTPException(
                status_code=status.HTTP_413_REQUEST_ENTITY_TOO_LARGE,
                detail="FRAME_TOO_LARGE",
            )

        chunks.append(chunk)

    if received_size == 0:
        raise HTTPException(
            status_code=status.HTTP_422_UNPROCESSABLE_ENTITY,
            detail="EMPTY_FRAME",
        )

    return b"".join(chunks)


def _looks_like_jpeg(data: bytes) -> bool:
    return (
            len(data) >= 4
            and data.startswith(b"\xff\xd8")
            and data.endswith(b"\xff\xd9")
    )