package com.isg.backend.violation.query;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

public record ViolationReviewRequest(
        @NotNull
        ViolationReviewStatus reviewStatus,

        @NotNull
        Long version
) {

    @JsonIgnore
    @AssertTrue(
            message = "reviewStatus must be REVIEWED, CONFIRMED or FALSE_ALARM"
    )
    public boolean isSupportedReviewStatus() {
        return reviewStatus == null
                || reviewStatus == ViolationReviewStatus.REVIEWED
                || reviewStatus == ViolationReviewStatus.CONFIRMED
                || reviewStatus == ViolationReviewStatus.FALSE_ALARM;
    }
}
