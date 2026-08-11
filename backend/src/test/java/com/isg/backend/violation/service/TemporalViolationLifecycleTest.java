package com.isg.backend.violation.service;

import com.isg.backend.violation.config.ViolationTemporalProperties;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.temporal.TemporalViolationTransitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalViolationLifecycleTest {

    private ViolationTemporalProperties properties;
    private TemporalConfirmationService service;

    private UUID cameraId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        properties =
                new ViolationTemporalProperties();

        properties.setConfirmationDuration(
                Duration.ofSeconds(1)
        );

        properties.setFrameGapTolerance(
                Duration.ofMillis(750)
        );

        properties.setCooldownDuration(
                Duration.ofSeconds(10)
        );

        service =
                new TemporalConfirmationService(
                        properties
                );

        cameraId =
                UUID.randomUUID();

        sessionId =
                UUID.randomUUID();
    }

    @Test
    void endsConfirmedViolationAfterGracePeriod() {
        Instant t0 =
                Instant.parse(
                        "2026-08-10T20:00:00Z"
                );

        confirmViolation(t0);

        TemporalViolationTransitions withinGrace =
                service.processFrameTransitions(
                        t0.plusMillis(1500),
                        List.of()
                );

        assertThat(withinGrace.ended())
                .isEmpty();

        TemporalViolationTransitions afterGrace =
                service.processFrameTransitions(
                        t0.plusSeconds(2),
                        List.of()
                );

        assertThat(afterGrace.ended())
                .hasSize(1);

        assertThat(
                afterGrace.ended()
                        .getFirst()
                        .cameraId()
        ).isEqualTo(
                cameraId
        );

        assertThat(
                afterGrace.ended()
                        .getFirst()
                        .sessionId()
        ).isEqualTo(
                sessionId
        );

        assertThat(
                afterGrace.ended()
                        .getFirst()
                        .violationType()
        ).isEqualTo(
                ViolationType.MISSING_WELDING_MASK
        );

        assertThat(
                afterGrace.ended()
                        .getFirst()
                        .endedAt()
        ).isEqualTo(
                t0.plusSeconds(1)
        );
    }

    @Test
    void doesNotProduceDuplicateEndEvent() {
        Instant t0 =
                Instant.parse(
                        "2026-08-10T20:00:00Z"
                );

        confirmViolation(t0);

        TemporalViolationTransitions firstEnd =
                service.processFrameTransitions(
                        t0.plusSeconds(2),
                        List.of()
                );

        TemporalViolationTransitions secondEnd =
                service.processFrameTransitions(
                        t0.plusSeconds(3),
                        List.of()
                );

        assertThat(firstEnd.ended())
                .hasSize(1);

        assertThat(secondEnd.ended())
                .isEmpty();
    }

    @Test
    void blocksNewConfirmationDuringCooldown() {
        Instant t0 =
                Instant.parse(
                        "2026-08-10T20:00:00Z"
                );

        confirmViolation(t0);

        service.processFrameTransitions(
                t0.plusSeconds(2),
                List.of()
        );

        Instant newCandidateStart =
                t0.plusSeconds(3);

        service.processFrameTransitions(
                newCandidateStart,
                List.of(
                        candidate(
                                newCandidateStart
                        )
                )
        );

        service.processFrameTransitions(
                newCandidateStart.plusMillis(500),
                List.of(
                        candidate(
                                newCandidateStart.plusMillis(500)
                        )
                )
        );

        TemporalViolationTransitions duringCooldown =
                service.processFrameTransitions(
                        newCandidateStart.plusSeconds(1),
                        List.of(
                                candidate(
                                        newCandidateStart.plusSeconds(1)
                                )
                        )
                );

        assertThat(duringCooldown.started())
                .isEmpty();
    }

    @Test
    void allowsNewConfirmationAfterCooldownExpires() {
        Instant t0 =
                Instant.parse(
                        "2026-08-10T20:00:00Z"
                );

        confirmViolation(t0);

        /*
         * Violation is ended at t0 + 2s.
         * Cooldown therefore lasts until t0 + 12s.
         */
        service.processFrameTransitions(
                t0.plusSeconds(2),
                List.of()
        );

        Instant newCandidateStart =
                t0.plusSeconds(12);

        service.processFrameTransitions(
                newCandidateStart,
                List.of(
                        candidate(
                                newCandidateStart
                        )
                )
        );

        service.processFrameTransitions(
                newCandidateStart.plusMillis(500),
                List.of(
                        candidate(
                                newCandidateStart.plusMillis(500)
                        )
                )
        );

        TemporalViolationTransitions confirmation =
                service.processFrameTransitions(
                        newCandidateStart.plusSeconds(1),
                        List.of(
                                candidate(
                                        newCandidateStart.plusSeconds(1)
                                )
                        )
                );

        assertThat(confirmation.started())
                .hasSize(1);
    }

    private void confirmViolation(
            Instant startedAt
    ) {
        TemporalViolationTransitions firstFrame =
                service.processFrameTransitions(
                        startedAt,
                        List.of(
                                candidate(
                                        startedAt
                                )
                        )
                );

        assertThat(firstFrame.started())
                .isEmpty();

        TemporalViolationTransitions secondFrame =
                service.processFrameTransitions(
                        startedAt.plusMillis(500),
                        List.of(
                                candidate(
                                        startedAt.plusMillis(500)
                                )
                        )
                );

        assertThat(secondFrame.started())
                .isEmpty();

        TemporalViolationTransitions confirmation =
                service.processFrameTransitions(
                        startedAt.plusSeconds(1),
                        List.of(
                                candidate(
                                        startedAt.plusSeconds(1)
                                )
                        )
                );

        assertThat(confirmation.started())
                .hasSize(1);
    }

    private CandidateViolation candidate(
            Instant timestamp
    ) {
        return new CandidateViolation(
                UUID.randomUUID(),
                cameraId,
                sessionId,
                "track-1",
                ViolationType.MISSING_WELDING_MASK,
                new BoundingBox(
                        0.10,
                        0.10,
                        0.30,
                        0.50
                ),
                timestamp,
                0.90
        );
    }
}