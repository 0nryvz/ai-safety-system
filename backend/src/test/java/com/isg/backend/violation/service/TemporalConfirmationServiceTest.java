package com.isg.backend.violation.service;

import com.isg.backend.violation.config.ViolationTemporalProperties;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TemporalConfirmationServiceTest {

    private ViolationTemporalProperties properties;
    private TemporalConfirmationService service;

    private UUID cameraId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        properties = new ViolationTemporalProperties();

        properties.setConfirmationDuration(
                Duration.ofMillis(1500)
        );

        properties.setFrameGapTolerance(
                Duration.ofMillis(750)
        );

        service = new TemporalConfirmationService(
                properties
        );

        cameraId = UUID.randomUUID();
        sessionId = UUID.randomUUID();
    }

    @Test
    void shouldNotConfirmSingleFrameCandidate() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0,
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-1",
                                        t0,
                                        0.90
                                )
                        )
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldConfirmOnceWhenConfirmationDurationIsReached() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.80
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(700),
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(1400),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(1400),
                                0.95
                        )
                )
        );

        List<ConfirmedViolation> confirmation =
                service.processFrame(
                        t0.plusMillis(1500),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-1",
                                        t0.plusMillis(1500),
                                        1.00
                                )
                        )
                );

        assertEquals(1, confirmation.size());

        List<ConfirmedViolation> duplicate =
                service.processFrame(
                        t0.plusMillis(1800),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-1",
                                        t0.plusMillis(1800),
                                        0.95
                                )
                        )
                );

        assertTrue(duplicate.isEmpty());
    }

    @Test
    void shouldNotConfirmBeforeConfirmationDuration() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(700),
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(1400),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(1400),
                                0.90
                        )
                )
        );

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0.plusMillis(1499),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-1",
                                        t0.plusMillis(1499),
                                        0.90
                                )
                        )
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldConfirmExactlyAtConfirmationDurationBoundary() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(700),
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(1400),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(1400),
                                0.90
                        )
                )
        );

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0.plusMillis(1500),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-1",
                                        t0.plusMillis(1500),
                                        0.90
                                )
                        )
                );

        assertEquals(1, result.size());
    }

    @Test
    void shouldKeepStateAcrossShortFrameGap() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(333),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(333),
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(666),
                List.of()
        );

        service.processFrame(
                t0.plusMillis(999),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(999),
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(1332),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(1332),
                                0.90
                        )
                )
        );

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0.plusMillis(1500),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-1",
                                        t0.plusMillis(1500),
                                        0.90
                                )
                        )
                );

        assertEquals(1, result.size());
    }

    @Test
    void shouldResetUnconfirmedStateAfterLongGap() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(800),
                List.of()
        );

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0.plusMillis(1500),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-1",
                                        t0.plusMillis(1500),
                                        0.90
                                )
                        )
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldStartNewCandidateAfterUnconfirmedStateExpires() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(800),
                List.of()
        );

        service.processFrame(
                t0.plusMillis(1000),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(1000),
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(1600),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(1600),
                                0.90
                        )
                )
        );

        List<ConfirmedViolation> tooEarly =
                service.processFrame(
                        t0.plusMillis(2000),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-1",
                                        t0.plusMillis(2000),
                                        0.90
                                )
                        )
                );

        assertTrue(tooEarly.isEmpty());
    }

    @Test
    void shouldKeepDifferentSessionsSeparated() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        UUID otherSessionId =
                UUID.randomUUID();

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(700),
                                0.90
                        )
                )
        );

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0.plusMillis(1400),
                        List.of(
                                candidate(
                                        cameraId,
                                        otherSessionId,
                                        "track-1",
                                        t0.plusMillis(1400),
                                        0.90
                                )
                        )
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldKeepDifferentCamerasSeparated() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        UUID otherCameraId =
                UUID.randomUUID();

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(700),
                                0.90
                        )
                )
        );

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0.plusMillis(1400),
                        List.of(
                                candidate(
                                        otherCameraId,
                                        sessionId,
                                        "track-1",
                                        t0.plusMillis(1400),
                                        0.90
                                )
                        )
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldKeepDifferentTrackedPersonsSeparated() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(700),
                                0.90
                        )
                )
        );

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0.plusMillis(1400),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-2",
                                        t0.plusMillis(1400),
                                        0.90
                                )
                        )
                );

        assertTrue(result.isEmpty());
    }

    @Test
    void shouldUseSameFallbackStateForUntrackedPersonsAcrossFrames() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "frame-event-a-person-0",
                                t0,
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "frame-event-b-person-0",
                                t0.plusMillis(700),
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(1400),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "frame-event-c-person-0",
                                t0.plusMillis(1400),
                                0.90
                        )
                )
        );

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0.plusMillis(1500),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "frame-event-d-person-0",
                                        t0.plusMillis(1500),
                                        0.90
                                )
                        )
                );

        assertEquals(1, result.size());
    }

    @Test
    void shouldAggregateConfidenceAcrossObservations() {
        Instant t0 =
                Instant.parse("2026-08-08T10:00:00Z");

        service.processFrame(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0,
                                0.60
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(700),
                                0.90
                        )
                )
        );

        service.processFrame(
                t0.plusMillis(1400),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-1",
                                t0.plusMillis(1400),
                                0.90
                        )
                )
        );

        List<ConfirmedViolation> result =
                service.processFrame(
                        t0.plusMillis(1500),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-1",
                                        t0.plusMillis(1500),
                                        0.90
                                )
                        )
                );

        assertEquals(1, result.size());

        assertEquals(
                0.825,
                result.getFirst().confidence(),
                0.0001
        );
    }

    private CandidateViolation candidate(
            UUID cameraId,
            UUID sessionId,
            String personKey,
            Instant timestamp,
            double confidence
    ) {
        return new CandidateViolation(
                UUID.randomUUID(),
                cameraId,
                sessionId,
                personKey,
                ViolationType.MISSING_GLOVES,
                new BoundingBox(
                        0.10,
                        0.10,
                        0.30,
                        0.50
                ),
                timestamp,
                confidence
        );
    }
}