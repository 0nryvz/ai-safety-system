package com.isg.backend.recording.dto;

import com.isg.backend.recording.domain.RecordingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.UUID;

public record RecordingCallbackRequest(

        @NotNull
        UUID recordingId,

        @NotNull
        UUID violationId,

        @NotNull
        RecordingStatus status,

        String objectKey,

        String coverImageKey,

        @Positive
        Integer durationMs,

        @Positive
        Long sizeBytes,

        String checksum,

        @NotNull
        @PositiveOrZero
        Integer retryCount,

        String errorCode
) {
}
