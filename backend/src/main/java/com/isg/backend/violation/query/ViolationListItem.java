package com.isg.backend.violation.query;

import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;

import java.time.Instant;
import java.util.UUID;

public record ViolationListItem(
        UUID violationId,
        UUID cameraId,
        UUID departmentId,
        ViolationType type,
        Instant startedAt,
        Instant endedAt,
        double confidence,
        ViolationLifecycleStatus lifecycleStatus,
        ViolationReviewStatus reviewStatus,
        String recordingStatus,
        Instant updatedAt
) {
}
