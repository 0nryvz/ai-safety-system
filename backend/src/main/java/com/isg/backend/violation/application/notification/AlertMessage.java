package com.isg.backend.violation.application.notification;

import com.isg.backend.violation.domain.ViolationType;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record AlertMessage(
        UUID violationId,
        ViolationType type,
        String cameraName,
        String departmentName,
        Instant startedAt,
        double confidence,
        String lifecycleStatus,
        boolean coverImageReady
) {

    public AlertMessage {
        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        Objects.requireNonNull(
                type,
                "type must not be null"
        );

        if (cameraName == null || cameraName.isBlank()) {
            throw new IllegalArgumentException(
                    "cameraName must not be blank"
            );
        }

        if (departmentName == null || departmentName.isBlank()) {
            throw new IllegalArgumentException(
                    "departmentName must not be blank"
            );
        }

        Objects.requireNonNull(
                startedAt,
                "startedAt must not be null"
        );

        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException(
                    "confidence must be between 0 and 1"
            );
        }

        if (lifecycleStatus == null || lifecycleStatus.isBlank()) {
            throw new IllegalArgumentException(
                    "lifecycleStatus must not be blank"
            );
        }
    }
}