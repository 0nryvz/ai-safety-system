from datetime import datetime, timedelta, timezone

from pydantic import BaseModel, ConfigDict, Field, field_validator


def _require_utc(value: datetime) -> datetime:
    if value.tzinfo is None or value.utcoffset() is None:
        raise ValueError("TIMESTAMP_MUST_INCLUDE_TIMEZONE")

    if value.utcoffset() != timedelta(0):
        raise ValueError("TIMESTAMP_MUST_BE_UTC")

    return value


class StartRecordingCommandRequest(BaseModel):
    command_id: str = Field(alias="commandId", min_length=1, max_length=128)
    recording_id: str = Field(alias="recordingId", min_length=1, max_length=128)
    violation_id: str = Field(alias="violationId", min_length=1, max_length=128)
    camera_id: str = Field(alias="cameraId", min_length=1, max_length=128)
    session_id: str = Field(alias="sessionId", min_length=1, max_length=128)
    started_at: datetime = Field(alias="startedAt")
    pre_buffer_seconds: int = Field(alias="preBufferSeconds", ge=0)
    post_buffer_seconds: int = Field(alias="postBufferSeconds", ge=0)
    max_clip_seconds: int = Field(alias="maxClipSeconds", gt=0)

    _started_at_utc = field_validator("started_at")(_require_utc)

    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
    )


class StopRecordingCommandRequest(BaseModel):
    command_id: str = Field(alias="commandId", min_length=1, max_length=128)
    violation_id: str = Field(alias="violationId", min_length=1, max_length=128)
    ended_at: datetime = Field(alias="endedAt")

    _ended_at_utc = field_validator("ended_at")(_require_utc)

    model_config = ConfigDict(
        populate_by_name=True,
        extra="forbid",
    )


class RecordingCommandAckResponse(BaseModel):
    command_id: str = Field(alias="commandId")
    violation_id: str = Field(alias="violationId")
    idempotent: bool

    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
    )
