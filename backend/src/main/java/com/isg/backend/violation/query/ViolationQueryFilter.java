package com.isg.backend.violation.query;

import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;

import java.time.Instant;
import java.util.UUID;

public record ViolationQueryFilter(
        Instant from,
        Instant to,
        ViolationType type,
        UUID cameraId,
        UUID departmentId,
        ViolationLifecycleStatus lifecycleStatus,
        ViolationReviewStatus reviewStatus
) {

    public ViolationQueryFilter {
        if (from != null
                && to != null
                && from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "from must not be after to"
            );
        }
    }
}