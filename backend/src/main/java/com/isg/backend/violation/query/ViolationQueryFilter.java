package com.isg.backend.violation.query;

import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.exception.InvalidViolationQueryException;

import java.time.Instant;
import java.util.UUID;

public record ViolationQueryFilter(
        Instant from,
        Instant to,
        ViolationType type,
        UUID cameraId,
        UUID departmentId,
        ViolationLifecycleStatus lifecycleStatus,
        ViolationReviewStatus reviewStatus,
        String recordingStatus
) {

    public ViolationQueryFilter {
        if (from != null
                && to != null
                && from.isAfter(to)) {
            throw new InvalidViolationQueryException(
                    "from must not be after to"
            );
        }
    }

    public ViolationQueryFilter(
            Instant from,
            Instant to,
            ViolationType type,
            UUID cameraId,
            UUID departmentId,
            ViolationLifecycleStatus lifecycleStatus,
            ViolationReviewStatus reviewStatus
    ) {
        this(
                from,
                to,
                type,
                cameraId,
                departmentId,
                lifecycleStatus,
                reviewStatus,
                null
        );
    }
}
