package com.isg.backend.violation.domain.temporal;

import com.isg.backend.violation.domain.ViolationType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record EndedViolation(
        ViolationStateKey stateKey,
        UUID cameraId,
        UUID sessionId,
        ViolationType violationType,
        Instant endedAt
) {

    public EndedViolation {
        Objects.requireNonNull(
                stateKey,
                "stateKey must not be null"
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
                endedAt,
                "endedAt must not be null"
        );
    }
}