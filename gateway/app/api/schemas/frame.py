from datetime import datetime

from pydantic import BaseModel, ConfigDict, Field


class FrameUploadResponse(BaseModel):
    accepted: bool
    camera_id: str = Field(alias="cameraId")
    session_id: str = Field(alias="sessionId")
    captured_at: datetime = Field(alias="capturedAt")
    size_bytes: int = Field(alias="sizeBytes")
    queue_depth: int = Field(alias="queueDepth")
    queue_capacity: int = Field(alias="queueCapacity")
    frame_count: int = Field(alias="frameCount")
    dropped_frame_count: int = Field(
        alias="droppedFrameCount",
    )

    model_config = ConfigDict(
        populate_by_name=True,
        serialize_by_alias=True,
    )