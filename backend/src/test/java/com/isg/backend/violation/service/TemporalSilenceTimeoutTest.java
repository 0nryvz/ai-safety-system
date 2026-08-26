package com.isg.backend.violation.service;

import com.isg.backend.violation.config.ViolationTemporalProperties;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.temporal.EndedViolation;
import com.isg.backend.violation.domain.temporal.TemporalViolationTransitions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class TemporalSilenceTimeoutTest {

    private TemporalConfirmationService service;

    private UUID cameraId;
    private UUID sessionId;

    @BeforeEach
    void setUp() {
        ViolationTemporalProperties properties =
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

        properties.setSilenceTimeout(
                Duration.ofSeconds(5)
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
    void confirmedViolationRemainsActiveUntilSilenceTimeout() {
        Instant t0 =
                Instant.parse(
                        "2026-08-14T12:00:00Z"
                );

        confirmViolation(
                t0
        );

        List<EndedViolation> beforeTimeout =
                service.findSilentConfirmedStates(
                        t0.plusSeconds(5)
                                .plusMillis(999)
                );

        assertThat(
                beforeTimeout
        ).isEmpty();

        List<EndedViolation> atTimeout =
                service.findSilentConfirmedStates(
                        t0.plusSeconds(6)
                );

        assertThat(
                atTimeout
        ).hasSize(
                1
        );

        EndedViolation endedViolation =
                atTimeout.getFirst();

        assertThat(
                endedViolation.cameraId()
        ).isEqualTo(
                cameraId
        );

        assertThat(
                endedViolation.sessionId()
        ).isEqualTo(
                sessionId
        );

        assertThat(
                endedViolation.violationType()
        ).isEqualTo(
                ViolationType.MISSING_WELDING_MASK
        );

        assertThat(
                endedViolation.endedAt()
        ).isEqualTo(
                t0.plusSeconds(6)
        );
    }

    @Test
    void silentConfirmedStateRemainsAvailableUntilAcknowledged() {
        Instant t0 =
                Instant.parse(
                        "2026-08-14T12:00:00Z"
                );

        confirmViolation(
                t0
        );

        List<EndedViolation> first =
                service.findSilentConfirmedStates(
                        t0.plusSeconds(6)
                );

        List<EndedViolation> retry =
                service.findSilentConfirmedStates(
                        t0.plusSeconds(7)
                );

        assertThat(first)
                .hasSize(
                        1
                );

        assertThat(retry)
                .hasSize(
                        1
                );

        assertThat(
                retry.getFirst()
                        .endedAt()
        ).isEqualTo(
                first.getFirst()
                        .endedAt()
        );
    }

    @Test
    void acknowledgedSilentEndIsNotReturnedAgain() {
        Instant t0 =
                Instant.parse(
                        "2026-08-14T12:00:00Z"
                );

        confirmViolation(
                t0
        );

        EndedViolation endedViolation =
                service.findSilentConfirmedStates(
                                t0.plusSeconds(6)
                        )
                        .getFirst();

        assertThat(
                service.acknowledgeSilentEnd(
                        endedViolation
                )
        ).isTrue();

        assertThat(
                service.findSilentConfirmedStates(
                        t0.plusSeconds(7)
                )
        ).isEmpty();
    }

    private void confirmViolation(
            Instant startedAt
    ) {
        service.processFrameTransitions(
                startedAt,
                List.of(
                        candidate(
                                startedAt
                        )
                )
        );

        service.processFrameTransitions(
                startedAt.plusMillis(500),
                List.of(
                        candidate(
                                startedAt.plusMillis(500)
                        )
                )
        );

        TemporalViolationTransitions confirmation =
                service.processFrameTransitions(
                        startedAt.plusSeconds(1),
                        List.of(
                                candidate(
                                        startedAt.plusSeconds(1)
                                )
                        )
                );

        assertThat(
                confirmation.started()
        ).hasSize(
                1
        );
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