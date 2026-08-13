package com.isg.backend.violation.integration;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.temporal.TemporalViolationTransitions;
import com.isg.backend.violation.service.TemporalConfirmationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
        properties = {
                "violation.temporal.confirmation-duration=1500ms",
                "violation.temporal.frame-gap-tolerance=750ms",
                "violation.temporal.cooldown-duration=10s"
        }
)
class TemporalConfirmationIntegrationTest {

    @Autowired
    private TemporalConfirmationService temporalConfirmationService;

    @Test
    void confirmsCandidateOnceAfterConfiguredDurationAndToleratesShortGap() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Instant t0 =
                Instant.parse(
                        "2026-08-13T10:00:00Z"
                );

        TemporalViolationTransitions first =
                temporalConfirmationService.processFrameTransitions(
                        t0,
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-worker-1",
                                        t0
                                )
                        )
                );

        assertThat(
                first.started()
        ).isEmpty();

        /*
         * One frame is missing.
         * The next observed candidate is still within
         * the configured 750 ms gap tolerance.
         */
        TemporalViolationTransitions gapFrame =
                temporalConfirmationService.processFrameTransitions(
                        t0.plusMillis(333),
                        List.of()
                );

        assertThat(
                gapFrame.started()
        ).isEmpty();

        assertThat(
                gapFrame.ended()
        ).isEmpty();

        TemporalViolationTransitions resumed =
                temporalConfirmationService.processFrameTransitions(
                        t0.plusMillis(666),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-worker-1",
                                        t0.plusMillis(666)
                                )
                        )
                );

        assertThat(
                resumed.started()
        ).isEmpty();

        temporalConfirmationService.processFrameTransitions(
                t0.plusMillis(1200),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "track-worker-1",
                                t0.plusMillis(1200)
                        )
                )
        );

        TemporalViolationTransitions confirmed =
                temporalConfirmationService.processFrameTransitions(
                        t0.plusMillis(1500),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-worker-1",
                                        t0.plusMillis(1500)
                                )
                        )
                );

        assertThat(
                confirmed.started()
        ).hasSize(
                1
        );

        assertThat(
                confirmed.started()
                        .getFirst()
                        .cameraId()
        ).isEqualTo(
                cameraId
        );

        assertThat(
                confirmed.started()
                        .getFirst()
                        .sessionId()
        ).isEqualTo(
                sessionId
        );

        assertThat(
                confirmed.started()
                        .getFirst()
                        .violationType()
        ).isEqualTo(
                ViolationType.MISSING_GLOVES
        );

        assertThat(
                confirmed.started()
                        .getFirst()
                        .candidateStartedAt()
        ).isEqualTo(
                t0
        );

        TemporalViolationTransitions duplicate =
                temporalConfirmationService.processFrameTransitions(
                        t0.plusMillis(1700),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "track-worker-1",
                                        t0.plusMillis(1700)
                                )
                        )
                );

        assertThat(
                duplicate.started()
        ).isEmpty();
    }

    @Test
    void keepsDifferentSessionsIndependent() {
        UUID cameraId =
                UUID.randomUUID();

        UUID firstSessionId =
                UUID.randomUUID();

        UUID secondSessionId =
                UUID.randomUUID();

        Instant t0 =
                Instant.parse(
                        "2026-08-13T11:00:00Z"
                );

        temporalConfirmationService.processFrameTransitions(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                firstSessionId,
                                "track-worker-1",
                                t0
                        )
                )
        );

        temporalConfirmationService.processFrameTransitions(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                firstSessionId,
                                "track-worker-1",
                                t0.plusMillis(700)
                        )
                )
        );

        TemporalViolationTransitions otherSession =
                temporalConfirmationService.processFrameTransitions(
                        t0.plusMillis(1500),
                        List.of(
                                candidate(
                                        cameraId,
                                        secondSessionId,
                                        "track-worker-1",
                                        t0.plusMillis(1500)
                                )
                        )
                );

        assertThat(
                otherSession.started()
        ).isEmpty();
    }

    @Test
    void usesUntrackedFallbackAcrossDifferentFrameLocalPersonKeys() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Instant t0 =
                Instant.parse(
                        "2026-08-13T12:00:00Z"
                );

        temporalConfirmationService.processFrameTransitions(
                t0,
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "frame-event-a-person-0",
                                t0
                        )
                )
        );

        temporalConfirmationService.processFrameTransitions(
                t0.plusMillis(700),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "frame-event-b-person-0",
                                t0.plusMillis(700)
                        )
                )
        );

        temporalConfirmationService.processFrameTransitions(
                t0.plusMillis(1400),
                List.of(
                        candidate(
                                cameraId,
                                sessionId,
                                "frame-event-c-person-0",
                                t0.plusMillis(1400)
                        )
                )
        );

        TemporalViolationTransitions confirmed =
                temporalConfirmationService.processFrameTransitions(
                        t0.plusMillis(1500),
                        List.of(
                                candidate(
                                        cameraId,
                                        sessionId,
                                        "frame-event-d-person-0",
                                        t0.plusMillis(1500)
                                )
                        )
                );

        assertThat(
                confirmed.started()
        ).hasSize(
                1
        );

        assertThat(
                confirmed.started()
                        .getFirst()
                        .stateKey()
                        .subjectKey()
        ).isEqualTo(
                "untracked"
        );
    }

    private static CandidateViolation candidate(
            UUID cameraId,
            UUID sessionId,
            String personKey,
            Instant timestamp
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
                0.90
        );
    }
}