package com.isg.backend.violation.service;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
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
public class ViolationLifecycleService {

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

        ViolationStatusHistoryJpaEntity history =
                new ViolationStatusHistoryJpaEntity(
                        UUID.randomUUID(),
                        violationId,
                        ViolationStatusKind.LIFECYCLE,
                        null,
                        ViolationLifecycleStatus.ACTIVE.name(),
                        null,
                        transitionTime,
                        "Violation confirmed"
                );

        statusHistoryRepository.save(
                history
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
                violationRepository.findById(
                        violationId
                ).orElseThrow(
                        () -> new IllegalStateException(
                                "Active violation not found: "
                                        + violationId
                        )
                );

        /*
         * Idempotency guard:
         * the same temporal end must never produce another stop event.
         */
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
}