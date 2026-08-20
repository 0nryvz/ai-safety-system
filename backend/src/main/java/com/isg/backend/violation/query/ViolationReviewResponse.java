package com.isg.backend.violation.query;

import com.isg.backend.violation.domain.ViolationReviewStatus;

import java.time.Instant;
import java.util.UUID;

public record ViolationReviewResponse(
        UUID violationId,
        ViolationReviewStatus reviewStatus,
        UUID reviewedBy,
        Instant reviewedAt,
        long version
) {
}