package com.isg.backend.reporting.dto;

import com.isg.backend.recording.domain.RecordingStatus;

import java.time.Instant;
import java.util.UUID;

public record RecentViolationResponse(
        UUID violationId,
        Instant detectedAt,
        Instant startedAt,
        String violationType,
        UUID cameraId,
        UUID departmentId,
        String departmentName,
        String cameraName,
        String cameraCode,
        String lifecycleStatus,
        String reviewStatus,
        RecordingStatus recordingStatus,
        Instant recordingReadyAt,
        Double confidence,
        String modelVersion
) {
}
