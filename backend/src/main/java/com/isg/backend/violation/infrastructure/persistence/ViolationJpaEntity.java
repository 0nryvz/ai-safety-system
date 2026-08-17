package com.isg.backend.violation.infrastructure.persistence;

import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "violations")
public class ViolationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "camera_id", nullable = false)
    private UUID cameraId;

    @Column(name = "department_id", nullable = false)
    private UUID departmentId;

    @Column(name = "camera_session_id")
    private UUID cameraSessionId;

    @Column(name = "restricted_zone_id")
    private UUID restrictedZoneId;

    @Enumerated(EnumType.STRING)
    @Column(name = "violation_type", nullable = false, length = 60)
    private ViolationType violationType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "confidence", nullable = false, precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "model_version", nullable = false, length = 60)
    private String modelVersion;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 20)
    private ViolationLifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "review_status", nullable = false, length = 20)
    private ViolationReviewStatus reviewStatus;

    @Column(name = "cover_image_key", length = 512)
    private String coverImageKey;

    @Column(name = "detected_at")
    private Instant detectedAt;

    @Column(name = "alert_sent_at")
    private Instant alertSentAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ViolationJpaEntity() {
    }

    public ViolationJpaEntity(
            UUID id,
            UUID cameraId,
            UUID departmentId,
            UUID cameraSessionId,
            UUID restrictedZoneId,
            ViolationType violationType,
            Instant startedAt,
            BigDecimal confidence,
            String modelVersion,
            ViolationLifecycleStatus lifecycleStatus,
            ViolationReviewStatus reviewStatus,
            Instant detectedAt
    ) {
        this.id = id;
        this.cameraId = cameraId;
        this.departmentId = departmentId;
        this.cameraSessionId = cameraSessionId;
        this.restrictedZoneId = restrictedZoneId;
        this.violationType = violationType;
        this.startedAt = startedAt;
        this.confidence = confidence;
        this.modelVersion = modelVersion;
        this.lifecycleStatus = lifecycleStatus;
        this.reviewStatus = reviewStatus;
        this.detectedAt = detectedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getCameraId() {
        return cameraId;
    }

    public UUID getDepartmentId() {
        return departmentId;
    }

    public UUID getCameraSessionId() {
        return cameraSessionId;
    }

    public UUID getRestrictedZoneId() {
        return restrictedZoneId;
    }

    public ViolationType getViolationType() {
        return violationType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public ViolationLifecycleStatus getLifecycleStatus() {
        return lifecycleStatus;
    }

    public ViolationReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public String getCoverImageKey() {
        return coverImageKey;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public Instant getAlertSentAt() {
        return alertSentAt;
    }

    public UUID getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public void markEnded(
            Instant endedAt
    ) {
        this.endedAt =
                endedAt;
    }

    public void changeLifecycleStatus(
            ViolationLifecycleStatus lifecycleStatus
    ) {
        this.lifecycleStatus =
                Objects.requireNonNull(
                        lifecycleStatus,
                        "lifecycleStatus must not be null"
                );
    }

    public void review(
            ViolationReviewStatus reviewStatus,
            UUID reviewedBy,
            Instant reviewedAt
    ) {
        Objects.requireNonNull(
                reviewStatus,
                "reviewStatus must not be null"
        );

        Objects.requireNonNull(
                reviewedBy,
                "reviewedBy must not be null"
        );

        Objects.requireNonNull(
                reviewedAt,
                "reviewedAt must not be null"
        );

        if (reviewStatus == ViolationReviewStatus.UNREVIEWED) {
            throw new IllegalArgumentException(
                    "reviewStatus must be REVIEWED, CONFIRMED or FALSE_ALARM"
            );
        }

        this.reviewStatus =
                reviewStatus;

        this.reviewedBy =
                reviewedBy;

        this.reviewedAt =
                reviewedAt;
    }
}