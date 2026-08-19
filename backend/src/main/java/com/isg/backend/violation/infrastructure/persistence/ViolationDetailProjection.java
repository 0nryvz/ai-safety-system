package com.isg.backend.violation.infrastructure.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public interface ViolationDetailProjection {

    UUID getViolationId();

    UUID getCameraId();

    String getCameraName();

    String getCameraCode();

    UUID getDepartmentId();

    String getDepartmentName();

    UUID getSessionId();

    String getType();

    BigDecimal getConfidence();

    String getModelVersion();

    Instant getDetectedAt();

    Instant getStartedAt();

    Instant getEndedAt();

    String getLifecycleStatus();

    String getReviewStatus();

    UUID getReviewedBy();

    Instant getReviewedAt();

    String getCoverImageKey();
}