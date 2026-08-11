package com.isg.backend.recording.application.callback;

import com.isg.backend.recording.domain.RecordingStatus;

import java.util.UUID;

public record RecordingStatusCallback(
        UUID recordingId,
        UUID violationId,
        RecordingStatus status,
        String objectKey,
        Long durationMs,
        Long sizeBytes,
        String checksum,
        String errorCode
) {
}
