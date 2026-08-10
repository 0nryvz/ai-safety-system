package com.isg.backend.violation.infrastructure.persistence;

import com.isg.backend.violation.domain.ViolationStatusKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "violation_status_history")
public class ViolationStatusHistoryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "violation_id", nullable = false)
    private UUID violationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_kind", nullable = false, length = 12)
    private ViolationStatusKind statusKind;

    @Column(name = "from_status", length = 20)
    private String fromStatus;

    @Column(name = "to_status", nullable = false, length = 20)
    private String toStatus;

    @Column(name = "changed_by")
    private UUID changedBy;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    @Column(name = "note", length = 500)
    private String note;

    protected ViolationStatusHistoryJpaEntity() {
    }

    public ViolationStatusHistoryJpaEntity(
            UUID id,
            UUID violationId,
            ViolationStatusKind statusKind,
            String fromStatus,
            String toStatus,
            UUID changedBy,
            Instant changedAt,
            String note
    ) {
        this.id = id;
        this.violationId = violationId;
        this.statusKind = statusKind;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
        this.note = note;
    }

    public UUID getId() {
        return id;
    }

    public UUID getViolationId() {
        return violationId;
    }

    public ViolationStatusKind getStatusKind() {
        return statusKind;
    }

    public String getFromStatus() {
        return fromStatus;
    }

    public String getToStatus() {
        return toStatus;
    }

    public UUID getChangedBy() {
        return changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public String getNote() {
        return note;
    }
}