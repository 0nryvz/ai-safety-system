package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.application.port.RecordingQueryPort;
import com.isg.backend.violation.application.port.RecordingQueryResult;
import com.isg.backend.violation.application.port.RecordingStatusCallbackPort;
import com.isg.backend.violation.config.ViolationTemporalProperties;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ViolationRecoveryAcceptanceTest {

    private SpringDataViolationRepository repository;

    private RecordingQueryPort recordingQueryPort;

    private RecordingStatusCallbackPort recordingStatusCallbackPort;

    private ViolationRecoveryService recoveryService;


    @BeforeEach
    void setUp() {

        repository =
                mock(SpringDataViolationRepository.class);

        recordingQueryPort =
                mock(RecordingQueryPort.class);

        recordingStatusCallbackPort =
                mock(RecordingStatusCallbackPort.class);

        CameraQueryService cameraQueryService =
                mock(CameraQueryService.class);

        when(
                cameraQueryService.findGatewaySessionId(
                        any()
                )
        ).thenAnswer(
                invocation ->
                        Optional.of(
                                invocation.getArgument(
                                        0
                                )
                        )
        );

        recoveryService =
                new ViolationRecoveryService(
                        repository,
                        new ActiveViolationRegistry(),
                        new TemporalConfirmationService(
                                new ViolationTemporalProperties()
                        ),
                        recordingQueryPort,
                        recordingStatusCallbackPort,
                        cameraQueryService
                );
    }


    @Test
    void restartRecoveryReconcilesReadyRecordingState() {

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-21T10:00:00Z"
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
                        BigDecimal.valueOf(0.95),
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
        )
                .thenReturn(
                        List.of()
                );

        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.PREPARING
                        )
                )
        )
                .thenReturn(
                        List.of(
                                violation
                        )
                );


        when(
                recordingQueryPort.findByViolationId(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        RecordingQueryResult.ready(
                                "clips/recovered.mp4"
                        )
                )
        );


        int recovered =
                recoveryService.recoverInterruptedViolations();


        assertThat(recovered)
                .isEqualTo(1);


        verify(
                recordingStatusCallbackPort
        )
                .recordingReady(
                        eq(violationId),
                        any()
                );
    }
}