package com.isg.backend.reporting.dto;

import java.time.Instant;
import java.util.UUID;

public record RecentViolationResponse(
        UUID violationId,
        Instant detectedAt,
        Instant startedAt,
        String violationType,
        UUID cameraId,
        UUID departmentId,
        String cameraName,
        String cameraCode,
        String lifecycleStatus,
        String reviewStatus,
        String recordingStatus,
        Instant recordingReadyAt,
        String recordingObjectKey,
        String coverImageKey,
        Double confidence,
        String modelVersion
) {
}
