from typing import Protocol

from app.domain.frame import FramePacket


class AIFrameClient(Protocol):
    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        ...


class NoOpAIFrameClient:
    async def send_frame(
            self,
            frame: FramePacket,
    ) -> None:
        return None
