package com.isg.backend.recording.application;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record StartRecordingCommand(
        UUID commandId,
        UUID violationId,
        UUID cameraId,
        UUID sessionId,
        Instant startedAt,
        int preBufferSeconds,
        int postBufferSeconds,
        int maxClipSeconds
) {
    public StartRecordingCommand {
        Objects.requireNonNull(commandId, "commandId cannot be null");
        Objects.requireNonNull(violationId, "violationId cannot be null");
        Objects.requireNonNull(cameraId, "cameraId cannot be null");
        Objects.requireNonNull(sessionId, "sessionId cannot be null");
        Objects.requireNonNull(startedAt, "startedAt cannot be null");

        if (preBufferSeconds < 0) {
            throw new IllegalArgumentException("preBufferSeconds cannot be negative");
        }

        if (postBufferSeconds < 0) {
            throw new IllegalArgumentException("postBufferSeconds cannot be negative");
        }

        if (maxClipSeconds <= 0) {
            throw new IllegalArgumentException("maxClipSeconds must be positive");
        }
    }
}
