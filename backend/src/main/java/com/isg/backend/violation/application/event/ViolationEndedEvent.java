package com.isg.backend.violation.application.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ViolationEndedEvent(
        UUID commandId,
        UUID violationId,
        Instant endedAt
) {

    public ViolationEndedEvent {
        Objects.requireNonNull(
                commandId,
                "commandId must not be null"
        );

        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        Objects.requireNonNull(
                endedAt,
                "endedAt must not be null"
        );
    }
}