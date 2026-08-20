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

class MissingGlovesRuleTest {

    private static final UUID EVENT_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID CAMERA_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );

    private final MissingGlovesRule rule =
            new MissingGlovesRule();


    @Test
    void supportsMissingGlovesViolationType() {
        assertThat(
                rule.supportedType()
        ).isEqualTo(
                ViolationType.MISSING_GLOVES
        );
    }


    @Test
    void producesCandidateWhenWeldingPersonHasNonGloves() {
        PersonContext person =
                new PersonContext(
                        "track-worker-1",
                        person(),
                        List.of(
                                welding(),
                                nonGloves()
                        )
                );

        Optional<CandidateViolation> result =
                rule.evaluate(
                        person,
                        frame()
                );

        assertThat(result)
                .isPresent();

        assertThat(result.orElseThrow().violationType())
                .isEqualTo(
                        ViolationType.MISSING_GLOVES
                );
    }


    @Test
    void doesNotProduceCandidateWhenWeldingPersonHasBothGlovesAndNonGloves() {
        PersonContext person =
                new PersonContext(
                        "track-worker-1",
                        person(),
                        List.of(
                                welding(),
                                gloves(),
                                nonGloves()
                        )
                );

        Optional<CandidateViolation> result =
                rule.evaluate(
                        person,
                        frame()
                );

        assertThat(result)
                .isEmpty();
    }


    @Test
    void doesNotProduceCandidateWhenWeldingPersonHasNoGlovesDetection() {
        PersonContext person =
                new PersonContext(
                        "track-worker-1",
                        person(),
                        List.of(
                                welding(),
                                nonGloves()
                        )
                );

        Optional<CandidateViolation> result =
                rule.evaluate(
                        person,
                        frame()
                );

        assertThat(result)
                .isEmpty();
    }


    @Test
    void doesNotProduceCandidateWhenWeldingPersonHasGloves() {
        PersonContext person =
                new PersonContext(
                        "track-worker-1",
                        person(),
                        List.of(
                                welding(),
                                gloves()
                        )
                );

        Optional<CandidateViolation> result =
                rule.evaluate(
                        person,
                        frame()
                );

        assertThat(result)
                .isEmpty();
    }


    @Test
    void doesNotProduceCandidateWhenPersonIsNotWelding() {
        PersonContext person =
                new PersonContext(
                        "track-worker-1",
                        person(),
                        List.of(
                                nonGloves()
                        )
                );

        Optional<CandidateViolation> result =
                rule.evaluate(
                        person,
                        frame()
                );

        assertThat(result)
                .isEmpty();
    }


    private static DetectedObject nonGloves() {
        return new DetectedObject(
                DetectionLabel.NON_GLOVES,
                "non_gloves",
                0.91,
                new BoundingBox(
                        0.2,
                        0.45,
                        0.1,
                        0.1
                ),
                null
        );
    }


    private static DetectedObject person() {
        return new DetectedObject(
                DetectionLabel.PERSON,
                "Person",
                0.95,
                new BoundingBox(
                        0.1,
                        0.1,
                        0.4,
                        0.8
                ),
                "worker-1"
        );
    }


    private static DetectedObject welding() {
        return new DetectedObject(
                DetectionLabel.WELDING,
                "welding",
                0.92,
                new BoundingBox(
                        0.2,
                        0.4,
                        0.1,
                        0.1
                ),
                null
        );
    }


    private static DetectedObject gloves() {
        return new DetectedObject(
                DetectionLabel.GLOVES,
                "gloves",
                0.91,
                new BoundingBox(
                        0.2,
                        0.45,
                        0.1,
                        0.1
                ),
                null
        );
    }

    private static DetectedObject nonGloves() {
        return new DetectedObject(
                DetectionLabel.NON_GLOVES,
                "non_gloves",
                0.91,
                new BoundingBox(
                        0.2,
                        0.45,
                        0.1,
                        0.1
                ),
                null
        );
    }

    private static DetectionFrame frame() {
        return new DetectionFrame(
                EVENT_ID,
                CAMERA_ID,
                SESSION_ID,
                Instant.parse(
                        "2026-08-07T10:00:00Z"
                ),
                "model-v1",
                25L,
                List.of()
        );
    }
}