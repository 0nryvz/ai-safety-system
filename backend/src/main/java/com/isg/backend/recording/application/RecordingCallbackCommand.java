package com.isg.backend.recording.application;

import com.isg.backend.recording.domain.RecordingStatus;

import java.util.UUID;

public record RecordingCallbackCommand(
        UUID recordingId,
        UUID violationId,
        RecordingStatus status,
        String objectKey,
        Integer durationMs,
        Long sizeBytes,
        String checksum,
        Integer retryCount,
        String errorCode
) {
}
