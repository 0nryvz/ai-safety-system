from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field

from app.domain.session import SessionStatus


class OpenSessionRequest(BaseModel):
    camera_id: str = Field(
        alias="cameraId",
        min_length=1,
        max_length=128,
    )
    session_id: str = Field(
        alias="sessionId",
        min_length=1,
        max_length=128,
    )
    session_token: str = Field(
        alias="sessionToken",
        min_length=1,
        max_length=512,
    )

    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
    )


class SessionActionRequest(BaseModel):
    camera_id: str = Field(
        alias="cameraId",
        min_length=1,
        max_length=128,
    )

    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
    )


class SessionResponse(BaseModel):
    camera_id: str = Field(alias="cameraId")
    session_id: str = Field(alias="sessionId")
    status: SessionStatus
    opened_at: datetime = Field(alias="openedAt")
    last_heartbeat_at: datetime = Field(alias="lastHeartbeatAt")
    frame_count: int = Field(alias="frameCount")
    dropped_frame_count: int = Field(alias="droppedFrameCount")

    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
    )


class OpenSessionResponse(BaseModel):
    created: bool
    session: SessionResponse