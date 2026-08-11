package com.isg.backend.violation.application.event;

import com.isg.backend.violation.domain.ViolationType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ViolationStartedEvent(
        UUID commandId,
        UUID violationId,
        UUID cameraId,
        UUID sessionId,
        ViolationType violationType,
        Instant startedAt
) {

    public ViolationStartedEvent {
        Objects.requireNonNull(
                commandId,
                "commandId must not be null"
        );

        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        Objects.requireNonNull(
                cameraId,
                "cameraId must not be null"
        );

        Objects.requireNonNull(
                sessionId,
                "sessionId must not be null"
        );

        Objects.requireNonNull(
                violationType,
                "violationType must not be null"
        );

        Objects.requireNonNull(
                startedAt,
                "startedAt must not be null"
        );
    }
}