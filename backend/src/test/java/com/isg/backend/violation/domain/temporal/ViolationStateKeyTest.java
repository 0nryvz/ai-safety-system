package com.isg.backend.violation.domain.temporal;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ViolationStateKeyTest {

    @Test
    void shouldUseTrackBasedSubjectKeyWhenTrackIdExists() {
        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        CandidateViolation candidate =
                candidate(
                        cameraId,
                        sessionId,
                        "track-42"
                );

        ViolationStateKey key =
                ViolationStateKey.from(candidate);

        assertEquals(cameraId, key.cameraId());
        assertEquals(sessionId, key.sessionId());
        assertEquals(
                ViolationType.MISSING_GLOVES,
                key.violationType()
        );
        assertEquals("track-42", key.subjectKey());
    }

    @Test
    void shouldUseSameFallbackKeyForUntrackedPersonsAcrossFrames() {
        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ViolationStateKey first =
                ViolationStateKey.from(
                        candidate(
                                cameraId,
                                sessionId,
                                "frame-event-1-person-0"
                        )
                );

        ViolationStateKey second =
                ViolationStateKey.from(
                        candidate(
                                cameraId,
                                sessionId,
                                "frame-event-2-person-0"
                        )
                );

        assertEquals(first, second);
        assertEquals("untracked", first.subjectKey());
    }

    @Test
    void shouldKeepDifferentSessionsSeparated() {
        UUID cameraId = UUID.randomUUID();

        ViolationStateKey first =
                ViolationStateKey.from(
                        candidate(
                                cameraId,
                                UUID.randomUUID(),
                                "track-7"
                        )
                );

        ViolationStateKey second =
                ViolationStateKey.from(
                        candidate(
                                cameraId,
                                UUID.randomUUID(),
                                "track-7"
                        )
                );

        assertNotEquals(first, second);
    }

    private CandidateViolation candidate(
            UUID cameraId,
            UUID sessionId,
            String personKey
    ) {
        return new CandidateViolation(
                UUID.randomUUID(),
                cameraId,
                sessionId,
                personKey,
                ViolationType.MISSING_GLOVES,
                new BoundingBox(
                        0.1,
                        0.1,
                        0.3,
                        0.5
                ),
                Instant.now(),
                0.90
        );
    }
}