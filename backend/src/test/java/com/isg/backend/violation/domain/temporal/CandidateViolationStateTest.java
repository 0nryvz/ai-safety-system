package com.isg.backend.violation.domain.temporal;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CandidateViolationStateTest {

    @Test
    void shouldInitializeFromFirstCandidate() {
        Instant timestamp =
                Instant.parse("2026-08-08T10:00:00Z");

        CandidateViolation candidate =
                candidate(timestamp, 0.80);

        CandidateViolationState state =
                new CandidateViolationState(candidate);

        assertEquals(
                timestamp,
                state.candidateStartedAt()
        );

        assertEquals(
                timestamp,
                state.lastSeenAt()
        );

        assertEquals(
                0.80,
                state.averageConfidence(),
                0.0001
        );

        assertEquals(
                1L,
                state.observationCount()
        );

        assertFalse(state.confirmed());
    }

    @Test
    void shouldUpdateLastSeenAndAverageConfidence() {
        Instant first =
                Instant.parse("2026-08-08T10:00:00Z");

        Instant second =
                Instant.parse("2026-08-08T10:00:00.500Z");

        CandidateViolationState state =
                new CandidateViolationState(
                        candidate(first, 0.80)
                );

        state.observe(
                candidate(second, 1.00)
        );

        assertEquals(
                second,
                state.lastSeenAt()
        );

        assertEquals(
                0.90,
                state.averageConfidence(),
                0.0001
        );

        assertEquals(
                2L,
                state.observationCount()
        );
    }

    @Test
    void shouldIgnoreOlderObservation() {
        Instant first =
                Instant.parse("2026-08-08T10:00:01Z");

        Instant older =
                Instant.parse("2026-08-08T10:00:00Z");

        CandidateViolationState state =
                new CandidateViolationState(
                        candidate(first, 0.80)
                );

        state.observe(
                candidate(older, 1.00)
        );

        assertEquals(
                first,
                state.lastSeenAt()
        );

        assertEquals(
                0.80,
                state.averageConfidence(),
                0.0001
        );

        assertEquals(
                1L,
                state.observationCount()
        );
    }

    @Test
    void shouldMarkStateAsConfirmed() {
        CandidateViolationState state =
                new CandidateViolationState(
                        candidate(
                                Instant.parse(
                                        "2026-08-08T10:00:00Z"
                                ),
                                0.90
                        )
                );

        state.markConfirmed();

        assertTrue(state.confirmed());
    }

    private CandidateViolation candidate(
            Instant timestamp,
            double confidence
    ) {
        return new CandidateViolation(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "track-1",
                ViolationType.MISSING_GLOVES,
                new BoundingBox(
                        0.1,
                        0.1,
                        0.3,
                        0.5
                ),
                timestamp,
                confidence
        );
    }
}