package com.isg.backend.violation.service;

import com.isg.backend.violation.config.ViolationTemporalProperties;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import com.isg.backend.violation.application.port.RecordingQueryPort;
import com.isg.backend.violation.application.port.RecordingStatusCallbackPort;
import com.isg.backend.violation.application.port.RecordingQueryResult;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationRecoveryServiceTest {

    private SpringDataViolationRepository repository;

    private ActiveViolationRegistry registry;

    private TemporalConfirmationService temporalService;

    private ViolationRecoveryService service;

    private RecordingQueryPort recordingQueryPort;

    private RecordingStatusCallbackPort recordingStatusCallbackPort;


    @BeforeEach
    void setUp() {

        repository =
                mock(
                        SpringDataViolationRepository.class
                );

        registry =
                new ActiveViolationRegistry();

        temporalService =
                new TemporalConfirmationService(
                        new ViolationTemporalProperties()
                );

        recordingQueryPort =
                mock(
                        RecordingQueryPort.class
                );

        recordingStatusCallbackPort =
                mock(
                        RecordingStatusCallbackPort.class
                );

        service =
                new ViolationRecoveryService(
                        repository,
                        registry,
                        temporalService,
                        recordingQueryPort,
                        recordingStatusCallbackPort
                );
    }


    @Test
    void restoresActiveViolationState() {

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-19T10:00:00Z"
                );

        ViolationJpaEntity violation =
                new ViolationJpaEntity(
                        violationId,
                        cameraId,
                        UUID.randomUUID(),
                        sessionId,
                        UUID.randomUUID(),
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        BigDecimal.valueOf(0.90),
                        "model-v1",
                        ViolationLifecycleStatus.ACTIVE,
                        ViolationReviewStatus.UNREVIEWED,
                        startedAt,
                        "track-1",
                        sessionId
                );


        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.ACTIVE
                        )
                )
        ).thenReturn(
                List.of(violation)
        );


        int result =
                service.recoverInterruptedViolations();


        assertThat(result)
                .isEqualTo(1);


        ViolationStateKey key =
                new ViolationStateKey(
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        "track-1"
                );


        assertThat(
                registry.find(key)
        )
                .contains(
                        violationId
                );
    }


    @Test
    void returnsZeroWhenNoActiveViolationsExist() {

        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.ACTIVE,
                                ViolationLifecycleStatus.PREPARING
                        )
                )
        )
                .thenReturn(
                        List.of()
                );


        assertThat(
                service.recoverInterruptedViolations()
        )
                .isZero();
    }

    @Test
    void readyRecordingStateIsReconciledDuringRecovery() {

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-19T10:00:00Z"
                );


        ViolationJpaEntity violation =
                new ViolationJpaEntity(
                        violationId,
                        cameraId,
                        UUID.randomUUID(),
                        sessionId,
                        UUID.randomUUID(),
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        BigDecimal.valueOf(0.90),
                        "model-v1",
                        ViolationLifecycleStatus.PREPARING,
                        ViolationReviewStatus.UNREVIEWED,
                        startedAt,
                        "track-1",
                        sessionId
                );


        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.PREPARING
                        )
                )
        ).thenReturn(
                List.of(violation)
        );


        when(
                recordingQueryPort.findByViolationId(
                        violationId
                )
        ).thenReturn(
                java.util.Optional.of(
                        RecordingQueryResult.ready(
                                "clips/test.mp4"
                        )
                )
        );


        int result =
                service.recoverInterruptedViolations();


        assertThat(result)
                .isEqualTo(1);


        verify(
                recordingStatusCallbackPort
        )
                .recordingReady(
                        org.mockito.ArgumentMatchers.eq(violationId),
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void preparingAndActiveSameStateKeyDoesNotCauseRecoveryConflict() {

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-19T10:00:00Z"
                );

        ViolationJpaEntity preparing =
                new ViolationJpaEntity(
                        UUID.randomUUID(),
                        cameraId,
                        departmentId,
                        sessionId,
                        UUID.randomUUID(),
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        BigDecimal.valueOf(0.90),
                        "model-v1",
                        ViolationLifecycleStatus.PREPARING,
                        ViolationReviewStatus.UNREVIEWED,
                        startedAt,
                        "track-1",
                        sessionId
                );

        UUID activeId =
                UUID.randomUUID();

        ViolationJpaEntity active =
                new ViolationJpaEntity(
                        activeId,
                        cameraId,
                        departmentId,
                        sessionId,
                        UUID.randomUUID(),
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        BigDecimal.valueOf(0.90),
                        "model-v1",
                        ViolationLifecycleStatus.ACTIVE,
                        ViolationReviewStatus.UNREVIEWED,
                        startedAt,
                        "track-1",
                        sessionId
                );


        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.ACTIVE
                        )
                )
        ).thenReturn(
                List.of(active)
        );


        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.PREPARING
                        )
                )
        ).thenReturn(
                List.of(preparing)
        );


        assertThatCode(
                () -> service.recoverInterruptedViolations()
        )
                .doesNotThrowAnyException();


        ViolationStateKey key =
                new ViolationStateKey(
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        "track-1"
                );


        assertThat(
                registry.find(key)
        )
                .contains(
                        activeId
                );
    }

    @Test
    void duplicatePreparingStateKeysDoNotBreakRecovery() {

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-19T10:00:00Z"
                );


        ViolationJpaEntity first =
                new ViolationJpaEntity(
                        UUID.randomUUID(),
                        cameraId,
                        departmentId,
                        sessionId,
                        UUID.randomUUID(),
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        BigDecimal.valueOf(0.90),
                        "model-v1",
                        ViolationLifecycleStatus.PREPARING,
                        ViolationReviewStatus.UNREVIEWED,
                        startedAt,
                        "track-1",
                        sessionId
                );


        ViolationJpaEntity second =
                new ViolationJpaEntity(
                        UUID.randomUUID(),
                        cameraId,
                        departmentId,
                        sessionId,
                        UUID.randomUUID(),
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        BigDecimal.valueOf(0.85),
                        "model-v1",
                        ViolationLifecycleStatus.PREPARING,
                        ViolationReviewStatus.UNREVIEWED,
                        startedAt,
                        "track-1",
                        sessionId
                );


        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.ACTIVE
                        )
                )
        ).thenReturn(
                List.of()
        );


        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.PREPARING
                        )
                )
        ).thenReturn(
                List.of(
                        first,
                        second
                )
        );


        assertThatCode(
                () -> service.recoverInterruptedViolations()
        )
                .doesNotThrowAnyException();


        assertThat(
                registry.size()
        )
                .isZero();
    }

    @Test
    void errorRecordingStateIsReconciledDuringRecovery() {

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-19T10:00:00Z"
                );


        ViolationJpaEntity violation =
                new ViolationJpaEntity(
                        violationId,
                        cameraId,
                        UUID.randomUUID(),
                        sessionId,
                        UUID.randomUUID(),
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        BigDecimal.valueOf(0.90),
                        "model-v1",
                        ViolationLifecycleStatus.PREPARING,
                        ViolationReviewStatus.UNREVIEWED,
                        startedAt,
                        "track-1",
                        sessionId
                );


        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.PREPARING
                        )
                )
        ).thenReturn(
                List.of(violation)
        );


        when(
                recordingQueryPort.findByViolationId(
                        violationId
                )
        ).thenReturn(
                java.util.Optional.of(
                        RecordingQueryResult.notReady(
                                "ERROR"
                        )
                )
        );


        int result =
                service.recoverInterruptedViolations();


        assertThat(result)
                .isEqualTo(1);


        verify(
                recordingStatusCallbackPort
        )
                .recordingError(
                        org.mockito.ArgumentMatchers.eq(violationId),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.eq("RECOVERY_ERROR")
                );

        ViolationStateKey key =
                new ViolationStateKey(
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        "track-1"
                );

        assertThat(
                registry.find(key)
        )
                .isEmpty();
    }

}
