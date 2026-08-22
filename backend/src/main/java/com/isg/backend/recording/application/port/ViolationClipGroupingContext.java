package com.isg.backend.recording.application.port;

import java.util.Objects;
import java.util.UUID;

public record ViolationClipGroupingContext(
        UUID cameraId,
        UUID cameraSessionId,
        String subjectKey
) {

    public ViolationClipGroupingContext {
        Objects.requireNonNull(
                cameraId,
                "cameraId cannot be null"
        );
        Objects.requireNonNull(
                cameraSessionId,
                "cameraSessionId cannot be null"
        );
        Objects.requireNonNull(
                subjectKey,
                "subjectKey cannot be null"
        );

        if (subjectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "subjectKey cannot be blank"
            );
        }
    }
}