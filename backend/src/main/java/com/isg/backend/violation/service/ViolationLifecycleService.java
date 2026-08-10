package com.isg.backend.violation.service;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.RecordingStatusCallbackPort;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationStatusKind;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationStatusHistoryRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.infrastructure.persistence.ViolationStatusHistoryJpaEntity;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ViolationLifecycleService
        implements RecordingStatusCallbackPort {

    private final SpringDataViolationRepository violationRepository;
    private final SpringDataViolationStatusHistoryRepository statusHistoryRepository;
    private final CameraService cameraService;
    private final ApplicationEventPublisher eventPublisher;

    public ViolationLifecycleService(
            SpringDataViolationRepository violationRepository,
            SpringDataViolationStatusHistoryRepository statusHistoryRepository,
            CameraService cameraService,
            ApplicationEventPublisher eventPublisher
    ) {
        this.violationRepository =
                violationRepository;

        this.statusHistoryRepository =
                statusHistoryRepository;

        this.cameraService =
                cameraService;

        this.eventPublisher =
                eventPublisher;
    }

    @Transactional
    public ViolationJpaEntity startViolation(
            ConfirmedViolation confirmedViolation,
            String modelVersion
    ) {
        Objects.requireNonNull(
                confirmedViolation,
                "confirmedViolation must not be null"
        );

        Objects.requireNonNull(
                modelVersion,
                "modelVersion must not be null"
        );

        CameraResponse camera =
                cameraService.getCameraById(
                        confirmedViolation.cameraId()
                );

        if (camera.getDepartmentId() == null) {
            throw new IllegalStateException(
                    "Camera department must not be null."
            );
        }

        UUID violationId =
                UUID.randomUUID();

        UUID commandId =
                UUID.randomUUID();

        Instant transitionTime =
                confirmedViolation.confirmedAt();

        ViolationJpaEntity violation =
                new ViolationJpaEntity(
                        violationId,
                        confirmedViolation.cameraId(),
                        camera.getDepartmentId(),
                        confirmedViolation.sessionId(),
                        null,
                        confirmedViolation.violationType(),
                        confirmedViolation.candidateStartedAt(),
                        BigDecimal.valueOf(
                                confirmedViolation.confidence()
                        ),
                        modelVersion,
                        ViolationLifecycleStatus.ACTIVE,
                        ViolationReviewStatus.UNREVIEWED,
                        confirmedViolation.candidateStartedAt()
                );

        ViolationJpaEntity savedViolation =
                violationRepository.save(
                        violation
                );

        saveLifecycleHistory(
                violationId,
                null,
                ViolationLifecycleStatus.ACTIVE,
                transitionTime,
                "Violation confirmed"
        );

        eventPublisher.publishEvent(
                new ViolationStartedEvent(
                        commandId,
                        violationId,
                        confirmedViolation.cameraId(),
                        confirmedViolation.sessionId(),
                        confirmedViolation.violationType(),
                        confirmedViolation.candidateStartedAt()
                )
        );

        return savedViolation;
    }

    @Transactional
    public void endViolation(
            UUID violationId,
            Instant endedAt
    ) {
        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        Objects.requireNonNull(
                endedAt,
                "endedAt must not be null"
        );

        ViolationJpaEntity violation =
                findViolation(
                        violationId
                );

        if (violation.getEndedAt() != null) {
            return;
        }

        violation.markEnded(
                endedAt
        );

        violationRepository.save(
                violation
        );

        eventPublisher.publishEvent(
                new ViolationEndedEvent(
                        UUID.randomUUID(),
                        violationId,
                        endedAt
                )
        );
    }

    @Override
    @Transactional
    public void recordingReady(
            UUID violationId,
            Instant changedAt
    ) {
        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        Objects.requireNonNull(
                changedAt,
                "changedAt must not be null"
        );

        ViolationJpaEntity violation =
                findViolation(
                        violationId
                );

        requireEnded(
                violation
        );

        ViolationLifecycleStatus currentStatus =
                violation.getLifecycleStatus();

        if (currentStatus == ViolationLifecycleStatus.COMPLETED) {
            return;
        }

        if (currentStatus == ViolationLifecycleStatus.ERROR) {
            throw new IllegalStateException(
                    "ERROR violation cannot be completed without retry."
            );
        }

        violation.changeLifecycleStatus(
                ViolationLifecycleStatus.COMPLETED
        );

        violationRepository.save(
                violation
        );

        saveLifecycleHistory(
                violationId,
                currentStatus,
                ViolationLifecycleStatus.COMPLETED,
                changedAt,
                "Recording ready"
        );
    }

    @Override
    @Transactional
    public void recordingError(
            UUID violationId,
            Instant changedAt,
            String errorCode
    ) {
        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        Objects.requireNonNull(
                changedAt,
                "changedAt must not be null"
        );

        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException(
                    "errorCode must not be blank"
            );
        }

        ViolationJpaEntity violation =
                findViolation(
                        violationId
                );

        requireEnded(
                violation
        );

        ViolationLifecycleStatus currentStatus =
                violation.getLifecycleStatus();

        if (currentStatus == ViolationLifecycleStatus.ERROR) {
            return;
        }

        if (currentStatus == ViolationLifecycleStatus.COMPLETED) {
            throw new IllegalStateException(
                    "COMPLETED violation cannot transition to ERROR."
            );
        }

        violation.changeLifecycleStatus(
                ViolationLifecycleStatus.ERROR
        );

        violationRepository.save(
                violation
        );

        saveLifecycleHistory(
                violationId,
                currentStatus,
                ViolationLifecycleStatus.ERROR,
                changedAt,
                "Recording error: " + errorCode
        );
    }

    private ViolationJpaEntity findViolation(
            UUID violationId
    ) {
        return violationRepository.findById(
                violationId
        ).orElseThrow(
                () -> new IllegalStateException(
                        "Violation not found: "
                                + violationId
                )
        );
    }

    private void requireEnded(
            ViolationJpaEntity violation
    ) {
        if (violation.getEndedAt() == null) {
            throw new IllegalStateException(
                    "Violation must be ended before recording reaches a terminal status."
            );
        }
    }

    private void saveLifecycleHistory(
            UUID violationId,
            ViolationLifecycleStatus fromStatus,
            ViolationLifecycleStatus toStatus,
            Instant changedAt,
            String note
    ) {
        statusHistoryRepository.save(
                new ViolationStatusHistoryJpaEntity(
                        UUID.randomUUID(),
                        violationId,
                        ViolationStatusKind.LIFECYCLE,
                        fromStatus == null
                                ? null
                                : fromStatus.name(),
                        toStatus.name(),
                        null,
                        changedAt,
                        note
                )
        );
    }
}