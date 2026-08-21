package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationRecordingUpdatedEvent;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.temporal.EndedViolation;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
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
import static org.mockito.Mockito.*;

class ViolationEndToEndAcceptanceTest {

    private ViolationSilenceWatchdog watchdog;

    private TemporalConfirmationService temporalService;
    private ActiveViolationRegistry registry;
    private ViolationLifecycleService lifecycleService;

    private SpringDataViolationRepository repository;
    private SpringDataViolationStatusHistoryRepository historyRepository;
    private CameraQueryService cameraQueryService;
    private ApplicationEventPublisher eventPublisher;


    @BeforeEach
    void setUp() {

        repository =
                mock(SpringDataViolationRepository.class);

        historyRepository =
                mock(SpringDataViolationStatusHistoryRepository.class);

        cameraQueryService =
                mock(CameraQueryService.class);

        eventPublisher =
                mock(ApplicationEventPublisher.class);


        lifecycleService =
                new ViolationLifecycleService(
                        repository,
                        historyRepository,
                        cameraQueryService,
                        eventPublisher
                );


        temporalService =
                mock(TemporalConfirmationService.class);

        registry =
                mock(ActiveViolationRegistry.class);


        watchdog =
                new ViolationSilenceWatchdog(
                        temporalService,
                        registry,
                        lifecycleService
                );
    }


    @Test
    void violationLifecycleCompletesAfterSilenceAndRecordingReady() {

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();


        Instant endedAt =
                Instant.parse(
                        "2026-08-21T10:00:05Z"
                );


        ViolationStateKey key =
                new ViolationStateKey(
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        "track-1"
                );


        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);


        when(
                repository.findById(
                        violationId
                )
        )
                .thenReturn(
                        Optional.of(violation)
                );


        when(
                violation.getEndedAt()
        )
                .thenReturn(
                        null,
                        endedAt
                );


        when(
                violation.getLifecycleStatus()
        )
                .thenReturn(
                        ViolationLifecycleStatus.ACTIVE,
                        ViolationLifecycleStatus.PREPARING
                );


        when(
                registry.find(
                        key
                )
        )
                .thenReturn(
                        Optional.of(
                                violationId
                        )
                );


        EndedViolation endedViolation =
                new EndedViolation(
                        key,
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        endedAt
                );


        when(
                temporalService.findSilentConfirmedStates(
                        any()
                )
        )
                .thenReturn(
                        java.util.List.of(
                                endedViolation
                        )
                );


        /*
         * 1- AI silence timeout sonrası violation end
         */
        watchdog.sweepAt(
                endedAt.plusSeconds(1)
        );


        /*
         * 2- Backend 4 recording READY callback sonrası lifecycle complete
         */
        lifecycleService.recordingReady(
                violationId,
                endedAt.plusSeconds(2)
        );


        verify(
                eventPublisher
        )
                .publishEvent(
                        any(ViolationEndedEvent.class)
                );


        verify(
                eventPublisher
        )
                .publishEvent(
                        any(ViolationRecordingUpdatedEvent.class)
                );
    }
}