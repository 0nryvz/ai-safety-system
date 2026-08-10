package com.isg.backend.violation.service;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationLifecycleServiceTest {

    private SpringDataViolationRepository violationRepository;
    private SpringDataViolationStatusHistoryRepository statusHistoryRepository;
    private CameraService cameraService;
    private ViolationLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        violationRepository =
                mock(SpringDataViolationRepository.class);

        statusHistoryRepository =
                mock(SpringDataViolationStatusHistoryRepository.class);

        cameraService =
                mock(CameraService.class);

        lifecycleService =
                new ViolationLifecycleService(
                        violationRepository,
                        statusHistoryRepository,
                        cameraService
                );
    }

    @Test
    void createsActiveUnreviewedViolationAndInitialLifecycleHistory() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant candidateStartedAt =
                Instant.parse("2026-08-10T20:00:00Z");

        Instant confirmedAt =
                candidateStartedAt.plusSeconds(2);

        ConfirmedViolation confirmedViolation =
                confirmedViolation(
                        cameraId,
                        sessionId,
                        candidateStartedAt,
                        confirmedAt
                );

        CameraResponse cameraResponse =
                CameraResponse.builder()
                        .id(cameraId)
                        .departmentId(departmentId)
                        .active(true)
                        .build();

        when(cameraService.getCameraById(cameraId))
                .thenReturn(cameraResponse);

        when(violationRepository.save(any(ViolationJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

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

        assertThat(savedViolation.getCameraId())
                .isEqualTo(cameraId);

        assertThat(savedViolation.getDepartmentId())
                .isEqualTo(departmentId);

        assertThat(savedViolation.getCameraSessionId())
                .isEqualTo(sessionId);

        assertThat(savedViolation.getViolationType())
                .isEqualTo(
                        ViolationType.MISSING_WELDING_MASK
                );

        assertThat(savedViolation.getStartedAt())
                .isEqualTo(
                        candidateStartedAt
                );

        assertThat(savedViolation.getConfidence())
                .isEqualByComparingTo(
                        "0.9"
                );

        assertThat(savedViolation.getModelVersion())
                .isEqualTo(
                        "welding-ppe-v1"
                );

        assertThat(savedViolation.getLifecycleStatus())
                .isEqualTo(
                        ViolationLifecycleStatus.ACTIVE
                );

        assertThat(savedViolation.getReviewStatus())
                .isEqualTo(
                        ViolationReviewStatus.UNREVIEWED
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

        assertThat(history.getId())
                .isNotNull();

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

        assertThat(history.getChangedBy())
                .isNull();

        assertThat(history.getChangedAt())
                .isEqualTo(
                        confirmedAt
                );

        assertThat(history.getNote())
                .isEqualTo(
                        "Violation confirmed"
                );
    }

    @Test
    void rejectsCameraWithoutDepartmentBeforePersistence() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Instant candidateStartedAt =
                Instant.parse("2026-08-10T20:00:00Z");

        Instant confirmedAt =
                candidateStartedAt.plusSeconds(2);

        ConfirmedViolation confirmedViolation =
                confirmedViolation(
                        cameraId,
                        sessionId,
                        candidateStartedAt,
                        confirmedAt
                );

        CameraResponse cameraResponse =
                CameraResponse.builder()
                        .id(cameraId)
                        .departmentId(null)
                        .active(true)
                        .build();

        when(cameraService.getCameraById(cameraId))
                .thenReturn(cameraResponse);

        assertThatThrownBy(
                () -> lifecycleService.startViolation(
                        confirmedViolation,
                        "welding-ppe-v1"
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessage(
                        "Camera department must not be null."
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