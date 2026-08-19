package com.isg.backend.violation.service;

import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.temporal.EndedViolation;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationSilenceWatchdogTest {

    private TemporalConfirmationService temporalConfirmationService;
    private ActiveViolationRegistry activeViolationRegistry;
    private ViolationLifecycleService violationLifecycleService;

    private ViolationSilenceWatchdog watchdog;

    @BeforeEach
    void setUp() {
        temporalConfirmationService =
                mock(
                        TemporalConfirmationService.class
                );

        activeViolationRegistry =
                mock(
                        ActiveViolationRegistry.class
                );

        violationLifecycleService =
                mock(
                        ViolationLifecycleService.class
                );

        watchdog =
                new ViolationSilenceWatchdog(
                        temporalConfirmationService,
                        activeViolationRegistry,
                        violationLifecycleService
                );
    }

    @Test
    void successfulSilenceEndAcknowledgesTemporalStateAndRemovesExactMapping() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        ViolationStateKey stateKey =
                new ViolationStateKey(
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        "track-worker-1"
                );

        Instant endedAt =
                Instant.parse(
                        "2026-08-14T12:00:06Z"
                );

        Instant sweepAt =
                endedAt.plusMillis(100);

        EndedViolation endedViolation =
                new EndedViolation(
                        stateKey,
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        endedAt
                );

        when(
                temporalConfirmationService.findSilentConfirmedStates(
                        sweepAt
                )
        ).thenReturn(
                List.of(
                        endedViolation
                )
        );

        when(
                activeViolationRegistry.find(
                        stateKey
                )
        ).thenReturn(
                Optional.of(
                        violationId
                )
        );

        watchdog.sweepAt(
                sweepAt
        );

        verify(
                violationLifecycleService
        ).endViolation(
                violationId,
                endedAt
        );

        verify(
                temporalConfirmationService
        ).acknowledgeSilentEnd(
                endedViolation
        );

        verify(
                activeViolationRegistry
        ).removeIfMappedTo(
                stateKey,
                violationId
        );
    }

    @Test
    void lifecycleFailureKeepsTemporalStateAndRegistryMappingForRetry() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        ViolationStateKey stateKey =
                new ViolationStateKey(
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        "track-worker-1"
                );

        Instant endedAt =
                Instant.parse(
                        "2026-08-14T12:00:06Z"
                );

        Instant sweepAt =
                endedAt.plusMillis(100);

        EndedViolation endedViolation =
                new EndedViolation(
                        stateKey,
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        endedAt
                );

        when(
                temporalConfirmationService.findSilentConfirmedStates(
                        sweepAt
                )
        ).thenReturn(
                List.of(
                        endedViolation
                )
        );

        when(
                activeViolationRegistry.find(
                        stateKey
                )
        ).thenReturn(
                Optional.of(
                        violationId
                )
        );

        doThrow(
                new IllegalStateException(
                        "database unavailable"
                )
        ).when(
                violationLifecycleService
        ).endViolation(
                violationId,
                endedAt
        );

        watchdog.sweepAt(
                sweepAt
        );

        verify(
                temporalConfirmationService,
                never()
        ).acknowledgeSilentEnd(
                endedViolation
        );

        verify(
                activeViolationRegistry,
                never()
        ).removeIfMappedTo(
                stateKey,
                violationId
        );
    }

    @Test
    void missingRegistryMappingDoesNotAcknowledgeTemporalEnd() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        ViolationStateKey stateKey =
                new ViolationStateKey(
                        cameraId,
                        sessionId,
                        ViolationType.UNPROTECTED_PERSON,
                        "track-worker-2"
                );

        Instant endedAt =
                Instant.parse(
                        "2026-08-14T12:00:06Z"
                );

        Instant sweepAt =
                endedAt.plusMillis(100);

        EndedViolation endedViolation =
                new EndedViolation(
                        stateKey,
                        cameraId,
                        sessionId,
                        ViolationType.UNPROTECTED_PERSON,
                        endedAt
                );

        when(
                temporalConfirmationService.findSilentConfirmedStates(
                        sweepAt
                )
        ).thenReturn(
                List.of(
                        endedViolation
                )
        );

        when(
                activeViolationRegistry.find(
                        stateKey
                )
        ).thenReturn(
                Optional.empty()
        );

        watchdog.sweepAt(
                sweepAt
        );

        verify(
                violationLifecycleService,
                never()
        ).endViolation(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );

        verify(
                temporalConfirmationService,
                never()
        ).acknowledgeSilentEnd(
                endedViolation
        );
    }
}