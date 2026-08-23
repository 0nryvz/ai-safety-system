package com.isg.backend.violation.application;

import java.util.Objects;
import java.util.UUID;

public record ViolationClipGroupingView(
        UUID cameraId,
        UUID cameraSessionId,
        String subjectKey
) {

    public ViolationClipGroupingView {
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