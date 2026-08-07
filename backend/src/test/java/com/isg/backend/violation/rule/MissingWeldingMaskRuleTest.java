package com.isg.backend.violation.rule;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MissingWeldingMaskRuleTest {

    private static final UUID EVENT_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CAMERA_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID SESSION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final MissingWeldingMaskRule rule =
            new MissingWeldingMaskRule();

    @Test
    void supportsMissingWeldingMaskViolationType() {
        assertThat(rule.supportedType())
                .isEqualTo(ViolationType.MISSING_WELDING_MASK);
    }

    @Test
    void producesCandidateWhenNonMaskDetectionIsAssociatedWithPerson() {
        PersonContext person = new PersonContext(
                "track-worker-1",
                person(),
                List.of(nonMask())
        );

        Optional<CandidateViolation> result =
                rule.evaluate(person, frame());

        assertThat(result).isPresent();

        CandidateViolation candidate = result.orElseThrow();

        assertThat(candidate.violationType())
                .isEqualTo(ViolationType.MISSING_WELDING_MASK);
        assertThat(candidate.personKey())
                .isEqualTo("track-worker-1");
        assertThat(candidate.eventId())
                .isEqualTo(EVENT_ID);
        assertThat(candidate.cameraId())
                .isEqualTo(CAMERA_ID);
        assertThat(candidate.sessionId())
                .isEqualTo(SESSION_ID);
        assertThat(candidate.personBox())
                .isEqualTo(person.person().boundingBox());
        assertThat(candidate.frameTimestamp())
                .isEqualTo(frame().frameTimestamp());
    }

    @Test
    void doesNotProduceCandidateWhenNonMaskDetectionIsAbsent() {
        PersonContext person = new PersonContext(
                "track-worker-1",
                person(),
                List.of(weldingMask())
        );

        Optional<CandidateViolation> result =
                rule.evaluate(person, frame());

        assertThat(result).isEmpty();
    }

    private static DetectedObject person() {
        return new DetectedObject(
                DetectionLabel.WELDER,
                "welder",
                0.95,
                new BoundingBox(0.1, 0.1, 0.4, 0.8),
                "worker-1"
        );
    }

    private static DetectedObject nonMask() {
        return new DetectedObject(
                DetectionLabel.NON_MASK,
                "non_mask",
                0.91,
                new BoundingBox(0.2, 0.15, 0.1, 0.1),
                null
        );
    }

    private static DetectedObject weldingMask() {
        return new DetectedObject(
                DetectionLabel.WELDING_MASK,
                "welding_mask",
                0.91,
                new BoundingBox(0.2, 0.15, 0.1, 0.1),
                null
        );
    }

    private static DetectionFrame frame() {
        return new DetectionFrame(
                EVENT_ID,
                CAMERA_ID,
                SESSION_ID,
                Instant.parse("2026-08-07T10:00:00Z"),
                "model-v1",
                25L,
                List.of()
        );
    }
}