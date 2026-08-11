package com.isg.backend.violation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DetectionRequest(

        @NotNull
        UUID eventId,

        @NotNull
        UUID cameraId,

        @NotNull
        UUID sessionId,

        @NotNull
        Instant frameTimestamp,

        @NotBlank
        String modelVersion,

        @NotNull
        @PositiveOrZero
        Long inferenceMs,

        @NotEmpty
        List<@Valid DetectionItem> detections
) {
}