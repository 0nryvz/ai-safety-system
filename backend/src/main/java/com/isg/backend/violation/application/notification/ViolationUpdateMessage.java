package com.isg.backend.violation.application.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ViolationUpdateMessage(
        UUID violationId,
        String lifecycleStatus,
        String recordingStatus,
        boolean clipReady,
        Instant updatedAt,
        String errorCode
) {

    public ViolationUpdateMessage {
        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        if (lifecycleStatus == null || lifecycleStatus.isBlank()) {
            throw new IllegalArgumentException(
                    "lifecycleStatus must not be blank"
            );
        }

        if (recordingStatus == null || recordingStatus.isBlank()) {
            throw new IllegalArgumentException(
                    "recordingStatus must not be blank"
            );
        }

        Objects.requireNonNull(
                updatedAt,
                "updatedAt must not be null"
        );
    }
}