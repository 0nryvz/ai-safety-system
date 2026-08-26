package com.isg.backend.violation.query;

import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;

import java.time.Instant;
import java.util.UUID;

public record ViolationDetailResponse(
        UUID violationId,
        UUID cameraId,
        String cameraName,
        String cameraCode,
        UUID departmentId,
        String departmentName,
        UUID sessionId,
        ViolationType type,
        double confidence,
        String modelVersion,
        Instant detectedAt,
        Instant startedAt,
        Instant endedAt,
        ViolationLifecycleStatus lifecycleStatus,
        ViolationReviewStatus reviewStatus,
        UUID reviewedBy,
        Instant reviewedAt,
        String recordingStatus,
        boolean clipReady,
        String playbackUrl,
        String coverImageKey,
        boolean coverImageReady,
        long version
) {
}