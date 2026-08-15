package com.isg.backend.violation.service;

import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.application.event.ViolationRecordingUpdatedEvent;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationStatusHistoryRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationRecordingNotificationTransitionTest {

    private SpringDataViolationRepository violationRepository;
    private SpringDataViolationStatusHistoryRepository statusHistoryRepository;
    private CameraService cameraService;
    private ApplicationEventPublisher eventPublisher;
    private CameraQueryService cameraQueryService;

    private ViolationLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        violationRepository =
                mock(SpringDataViolationRepository.class);

        statusHistoryRepository =
                mock(SpringDataViolationStatusHistoryRepository.class);

        cameraService =
                mock(CameraService.class);

        cameraQueryService =
                mock(CameraQueryService.class);

        eventPublisher =
                mock(ApplicationEventPublisher.class);

        lifecycleService =
                new ViolationLifecycleService(
                        violationRepository,
                        statusHistoryRepository,
                        cameraService,
                        cameraQueryService,
                        eventPublisher
                );
    }

    @Test
    void readyTransitionPublishesExactlyOneUpdateEvent() {
        UUID violationId =
                UUID.randomUUID();

        Instant changedAt =
                Instant.now();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(
                        changedAt.minusSeconds(1)
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.ACTIVE,
                        ViolationLifecycleStatus.COMPLETED
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        lifecycleService.recordingReady(
                violationId,
                changedAt
        );

        lifecycleService.recordingReady(
                violationId,
                changedAt.plusMillis(100)
        );

        verify(
                eventPublisher,
                times(1)
        ).publishEvent(
                any(
                        ViolationRecordingUpdatedEvent.class
                )
        );
    }

    @Test
    void errorTransitionPublishesExactlyOneUpdateEvent() {
        UUID violationId =
                UUID.randomUUID();

        Instant changedAt =
                Instant.now();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(
                        changedAt.minusSeconds(1)
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.ACTIVE,
                        ViolationLifecycleStatus.ERROR
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        lifecycleService.recordingError(
                violationId,
                changedAt,
                "ENCODER_FAILED"
        );

        lifecycleService.recordingError(
                violationId,
                changedAt.plusMillis(100),
                "ENCODER_FAILED"
        );

        verify(
                eventPublisher,
                times(1)
        ).publishEvent(
                any(
                        ViolationRecordingUpdatedEvent.class
                )
        );
    }

    @Test
    void duplicateReadyDoesNotCreateAnotherUpdateEvent() {
        UUID violationId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(
                        Instant.now()
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.COMPLETED
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        lifecycleService.recordingReady(
                violationId,
                Instant.now()
        );

        verify(
                eventPublisher,
                never()
        ).publishEvent(
                any(
                        ViolationRecordingUpdatedEvent.class
                )
        );
    }

    @Test
    void duplicateErrorDoesNotCreateAnotherUpdateEvent() {
        UUID violationId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getEndedAt())
                .thenReturn(
                        Instant.now()
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.ERROR
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        lifecycleService.recordingError(
                violationId,
                Instant.now(),
                "ENCODER_FAILED"
        );

        verify(
                eventPublisher,
                never()
        ).publishEvent(
                any(
                        ViolationRecordingUpdatedEvent.class
                )
        );
    }
}