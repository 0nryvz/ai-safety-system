package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationStatusKind;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationStatusHistoryRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.infrastructure.persistence.ViolationStatusHistoryJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationLifecycleServiceTest {
    private CameraQueryService cameraQueryService;
    private SpringDataViolationRepository violationRepository;
    private SpringDataViolationStatusHistoryRepository statusHistoryRepository;
    private ApplicationEventPublisher eventPublisher;
    private ViolationLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        cameraQueryService =
                mock(CameraQueryService.class);

        violationRepository =
                mock(SpringDataViolationRepository.class);

        statusHistoryRepository =
                mock(SpringDataViolationStatusHistoryRepository.class);

        eventPublisher =
                mock(ApplicationEventPublisher.class);

        lifecycleService =
                new ViolationLifecycleService(
                        violationRepository,
                        statusHistoryRepository,
                        cameraQueryService,
                        eventPublisher
                );
    }

    @Test
    void createsActiveUnreviewedViolationHistoryAndStartEvent() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        UUID cameraSessionRecordId =
                UUID.randomUUID();

        Instant candidateStartedAt =
                Instant.parse(
                        "2026-08-10T20:00:00Z"
                );

        Instant confirmedAt =
                candidateStartedAt.plusSeconds(2);

        ConfirmedViolation confirmedViolation =
                confirmedViolation(
                        cameraId,
                        sessionId,
                        candidateStartedAt,
                        confirmedAt
                );


        when(
                cameraQueryService.findDepartmentId(
                        cameraId
                )
        ).thenReturn(
                Optional.of(
                        departmentId
                )
        );

        when(
                cameraQueryService.findSessionRecordId(
                        cameraId,
                        sessionId
                )
        ).thenReturn(
                Optional.of(
                        cameraSessionRecordId
                )
        );

        when(
                violationRepository.save(
                        any(ViolationJpaEntity.class)
                )
        ).thenAnswer(
                invocation ->
                        invocation.getArgument(0)
        );

        lifecycleService.startViolation(
                confirmedViolation,
                "welding-ppe-v1"
        );

        ArgumentCaptor<ViolationJpaEntity> violationCaptor =
                ArgumentCaptor.forClass(
                        ViolationJpaEntity.class
                );

        verify(violationRepository)
                .save(
                        violationCaptor.capture()
                );

        ViolationJpaEntity savedViolation =
                violationCaptor.getValue();

        assertThat(savedViolation.getId())
                .isNotNull();

        assertThat(savedViolation.getDepartmentId())
                .isEqualTo(departmentId);

        assertThat(savedViolation.getCameraSessionId())
                .isEqualTo(
                        cameraSessionRecordId
                );

        assertThat(savedViolation.getLifecycleStatus())
                .isEqualTo(
                        ViolationLifecycleStatus.ACTIVE
                );

        assertThat(savedViolation.getReviewStatus())
                .isEqualTo(
                        ViolationReviewStatus.UNREVIEWED
                );

        assertThat(
                savedViolation.getCameraSessionId()
        ).isEqualTo(
                cameraSessionRecordId
        );

        ArgumentCaptor<ViolationStatusHistoryJpaEntity> historyCaptor =
                ArgumentCaptor.forClass(
                        ViolationStatusHistoryJpaEntity.class
                );

        verify(statusHistoryRepository)
                .save(
                        historyCaptor.capture()
                );

        ViolationStatusHistoryJpaEntity history =
                historyCaptor.getValue();

        assertThat(history.getViolationId())
                .isEqualTo(
                        savedViolation.getId()
                );

        assertThat(history.getStatusKind())
                .isEqualTo(
                        ViolationStatusKind.LIFECYCLE
                );

        assertThat(history.getFromStatus())
                .isNull();

        assertThat(history.getToStatus())
                .isEqualTo(
                        ViolationLifecycleStatus.ACTIVE.name()
                );

        ArgumentCaptor<ViolationStartedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        ViolationStartedEvent.class
                );

        verify(eventPublisher)
                .publishEvent(
                        eventCaptor.capture()
                );

        ViolationStartedEvent event =
                eventCaptor.getValue();

        assertThat(event.commandId())
                .isNotNull();

        assertThat(event.violationId())
                .isEqualTo(
                        savedViolation.getId()
                );

        assertThat(event.cameraId())
                .isEqualTo(cameraId);

        assertThat(event.sessionId())
                .isEqualTo(sessionId);

        assertThat(event.startedAt())
                .isEqualTo(
                        candidateStartedAt
                );
    }

    @Test
    void rejectsStartWhenActiveCameraSessionRecordCannotBeResolved() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-10T20:00:00Z"
                );

        ConfirmedViolation confirmedViolation =
                confirmedViolation(
                        cameraId,
                        sessionId,
                        startedAt,
                        startedAt.plusSeconds(2)
                );

        when(
                cameraQueryService.findDepartmentId(
                        cameraId
                )
        ).thenReturn(
                Optional.of(
                        departmentId
                )
        );

        when(
                cameraQueryService.findSessionRecordId(
                        cameraId,
                        sessionId
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () -> lifecycleService.startViolation(
                        confirmedViolation,
                        "welding-ppe-v1"
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        "Active camera session record not found"
                );

        verify(
                violationRepository,
                never()
        ).save(
                any()
        );

        verify(
                eventPublisher,
                never()
        ).publishEvent(
                any()
        );
    }

    @Test
    void recordingErrorIsIdempotentWhenAlreadyError() {
        UUID violationId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-10T20:00:05Z"
                        )
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.ERROR
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(violation)
        );

        lifecycleService.recordingError(
                violationId,
                Instant.parse(
                        "2026-08-10T20:00:07Z"
                ),
                "UPLOAD_FAILED"
        );

        verify(
                violation,
                never()
        ).changeLifecycleStatus(
                any()
        );

        verify(
                violationRepository,
                never()
        ).save(
                violation
        );

        verify(
                statusHistoryRepository,
                never()
        ).save(
                any()
        );
    }

    @Test
    void rejectsCameraWithoutDepartmentBeforePersistenceOrEvent() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-10T20:00:00Z"
                );

        ConfirmedViolation confirmedViolation =
                confirmedViolation(
                        cameraId,
                        sessionId,
                        startedAt,
                        startedAt.plusSeconds(2)
                );

        when(
                cameraQueryService.findDepartmentId(
                        cameraId
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () -> lifecycleService.startViolation(
                        confirmedViolation,
                        "welding-ppe-v1"
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                );

        verify(
                violationRepository,
                never()
        ).save(
                any()
        );

        verify(
                statusHistoryRepository,
                never()
        ).save(
                any()
        );

        verify(
                eventPublisher,
                never()
        ).publishEvent(
                any()
        );
    }

    @Test
    void endsViolationAndPublishesSingleStopEvent() {
        UUID violationId =
                UUID.randomUUID();

        Instant endedAt =
                Instant.parse(
                        "2026-08-10T20:00:05Z"
                );

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(null);

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(violation)
        );

        lifecycleService.endViolation(
                violationId,
                endedAt
        );

        verify(violation)
                .markEnded(
                        endedAt
                );

        verify(violationRepository)
                .save(
                        violation
                );

        ArgumentCaptor<ViolationEndedEvent> eventCaptor =
                ArgumentCaptor.forClass(
                        ViolationEndedEvent.class
                );

        verify(eventPublisher)
                .publishEvent(
                        eventCaptor.capture()
                );

        ViolationEndedEvent event =
                eventCaptor.getValue();

        assertThat(event.commandId())
                .isNotNull();

        assertThat(event.violationId())
                .isEqualTo(
                        violationId
                );

        assertThat(event.endedAt())
                .isEqualTo(
                        endedAt
                );
    }

    @Test
    void doesNotPublishSecondStopEventWhenViolationAlreadyEnded() {
        UUID violationId =
                UUID.randomUUID();

        Instant existingEndedAt =
                Instant.parse(
                        "2026-08-10T20:00:05Z"
                );

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(
                        existingEndedAt
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(violation)
        );

        lifecycleService.endViolation(
                violationId,
                existingEndedAt.plusSeconds(1)
        );

        verify(
                violation,
                never()
        ).markEnded(
                any()
        );

        verify(
                violationRepository,
                never()
        ).save(
                violation
        );

        verify(
                eventPublisher,
                never()
        ).publishEvent(
                any()
        );
    }

    @Test
    void recordingReadyTransitionsEndedViolationToCompletedWithHistory() {
        UUID violationId =
                UUID.randomUUID();

        Instant endedAt =
                Instant.parse("2026-08-10T20:00:05Z");

        Instant readyAt =
                endedAt.plusSeconds(2);

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(endedAt);

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.ACTIVE
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(violation)
        );

        lifecycleService.recordingReady(
                violationId,
                readyAt
        );

        verify(violation)
                .changeLifecycleStatus(
                        ViolationLifecycleStatus.COMPLETED
                );

        verify(violationRepository)
                .save(
                        violation
                );

        ArgumentCaptor<ViolationStatusHistoryJpaEntity> historyCaptor =
                ArgumentCaptor.forClass(
                        ViolationStatusHistoryJpaEntity.class
                );

        verify(statusHistoryRepository)
                .save(
                        historyCaptor.capture()
                );

        ViolationStatusHistoryJpaEntity history =
                historyCaptor.getValue();

        assertThat(history.getViolationId())
                .isEqualTo(violationId);

        assertThat(history.getFromStatus())
                .isEqualTo(
                        ViolationLifecycleStatus.ACTIVE.name()
                );

        assertThat(history.getToStatus())
                .isEqualTo(
                        ViolationLifecycleStatus.COMPLETED.name()
                );

        assertThat(history.getChangedAt())
                .isEqualTo(readyAt);
    }

    @Test
    void recordingErrorTransitionsEndedViolationToErrorWithHistory() {
        UUID violationId =
                UUID.randomUUID();

        Instant endedAt =
                Instant.parse("2026-08-10T20:00:05Z");

        Instant errorAt =
                endedAt.plusSeconds(1);

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(endedAt);

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.ACTIVE
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(violation)
        );

        lifecycleService.recordingError(
                violationId,
                errorAt,
                "UPLOAD_FAILED"
        );

        verify(violation)
                .changeLifecycleStatus(
                        ViolationLifecycleStatus.ERROR
                );

        verify(violationRepository)
                .save(
                        violation
                );

        ArgumentCaptor<ViolationStatusHistoryJpaEntity> historyCaptor =
                ArgumentCaptor.forClass(
                        ViolationStatusHistoryJpaEntity.class
                );

        verify(statusHistoryRepository)
                .save(
                        historyCaptor.capture()
                );

        ViolationStatusHistoryJpaEntity history =
                historyCaptor.getValue();

        assertThat(history.getFromStatus())
                .isEqualTo(
                        ViolationLifecycleStatus.ACTIVE.name()
                );

        assertThat(history.getToStatus())
                .isEqualTo(
                        ViolationLifecycleStatus.ERROR.name()
                );

        assertThat(history.getNote())
                .contains(
                        "UPLOAD_FAILED"
                );
    }

    @Test
    void recordingReadyIsIdempotentWhenAlreadyCompleted() {
        UUID violationId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(
                        Instant.parse(
                                "2026-08-10T20:00:05Z"
                        )
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.COMPLETED
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(violation)
        );

        lifecycleService.recordingReady(
                violationId,
                Instant.parse(
                        "2026-08-10T20:00:07Z"
                )
        );

        verify(
                violation,
                never()
        ).changeLifecycleStatus(
                any()
        );

        verify(
                statusHistoryRepository,
                never()
        ).save(
                any()
        );
    }

    @Test
    void recordingReadyDoesNotCompleteActiveViolationBeforeItEnds() {
        UUID violationId =
                UUID.randomUUID();

        Instant readyAt =
                Instant.parse(
                        "2026-08-10T20:00:07Z"
                );

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(null);

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.ACTIVE
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(violation)
        );

        lifecycleService.recordingReady(
                violationId,
                readyAt
        );

        verify(
                violation,
                never()
        ).changeLifecycleStatus(
                any()
        );

        verify(
                violationRepository,
                never()
        ).save(
                violation
        );

        verify(
                statusHistoryRepository,
                never()
        ).save(
                any()
        );
    }

    @Test
    void rejectsEndForUnknownViolation() {
        UUID violationId =
                UUID.randomUUID();

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () -> lifecycleService.endViolation(
                        violationId,
                        Instant.parse(
                                "2026-08-10T20:00:05Z"
                        )
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        violationId.toString()
                );

        verify(
                eventPublisher,
                never()
        ).publishEvent(
                any()
        );
    }

    private ConfirmedViolation confirmedViolation(
            UUID cameraId,
            UUID sessionId,
            Instant candidateStartedAt,
            Instant confirmedAt
    ) {
        ViolationStateKey stateKey =
                new ViolationStateKey(
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_WELDING_MASK,
                        "track-1"
                );

        return new ConfirmedViolation(
                stateKey,
                cameraId,
                sessionId,
                ViolationType.MISSING_WELDING_MASK,
                candidateStartedAt,
                confirmedAt,
                0.90
        );
    }
}
