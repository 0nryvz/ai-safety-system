package com.isg.backend.violation.query;

import com.isg.backend.violation.domain.ViolationReviewStatus;

import java.util.Objects;
import java.util.UUID;

public record ViolationReviewCommand(
        UUID violationId,
        ViolationReviewStatus reviewStatus,
        UUID reviewerId,
        long version
) {

    public ViolationReviewCommand {
        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        Objects.requireNonNull(
                reviewStatus,
                "reviewStatus must not be null"
        );

        Objects.requireNonNull(
                reviewerId,
                "reviewerId must not be null"
        );

        if (reviewStatus == ViolationReviewStatus.UNREVIEWED) {
            throw new IllegalArgumentException(
                    "reviewStatus must be REVIEWED, CONFIRMED or FALSE_ALARM"
            );
        }

        if (version < 0) {
            throw new IllegalArgumentException(
                    "version must not be negative"
            );
        }
    }
}
