package com.isg.backend.recording.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StopRecordingCommand(
        UUID commandId,
        UUID violationId,
        Instant endedAt
) {
    public StopRecordingCommand {
        Objects.requireNonNull(commandId, "commandId cannot be null");
        Objects.requireNonNull(violationId, "violationId cannot be null");
        Objects.requireNonNull(endedAt, "endedAt cannot be null");
    }
}
