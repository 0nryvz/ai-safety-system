package com.isg.backend.violation.rule;

import com.isg.backend.violation.config.ViolationRuleProperties;
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
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CandidateViolationEvaluatorTest {

    private static final UUID EVENT_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CAMERA_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID SESSION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void evaluatesRulesForMatchedPerson() {
        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        CandidateViolationEvaluator evaluator =
                new CandidateViolationEvaluator(
                        List.of(
                                new AlwaysCandidateRule(
                                        ViolationType.MISSING_GLOVES
                                )
                        ),
                        new PersonPpeMatcher(),
                        properties
                );

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(person(0.95, "worker-1"))
                );

        assertThat(result).hasSize(1);

        CandidateViolation candidate = result.get(0);

        assertThat(candidate.violationType())
                .isEqualTo(ViolationType.MISSING_GLOVES);

        assertThat(candidate.personKey())
                .isEqualTo("track-worker-1");
    }

    @Test
    void doesNotEvaluatePersonBelowConfiguredConfidenceThreshold() {
        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        properties.setConfidenceThresholds(Map.of(
                ViolationType.MISSING_GLOVES,
                0.60
        ));

        CandidateViolationEvaluator evaluator =
                new CandidateViolationEvaluator(
                        List.of(
                                new AlwaysCandidateRule(
                                        ViolationType.MISSING_GLOVES
                                )
                        ),
                        new PersonPpeMatcher(),
                        properties
                );

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(person(0.40, "worker-1"))
                );

        assertThat(result).isEmpty();
    }

    @Test
    void producesSingleCandidateForSamePersonAndViolationType() {
        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        CandidateViolationEvaluator evaluator =
                new CandidateViolationEvaluator(
                        List.of(
                                new AlwaysCandidateRule(
                                        ViolationType.MISSING_GLOVES
                                ),
                                new AlwaysCandidateRule(
                                        ViolationType.MISSING_GLOVES
                                )
                        ),
                        new PersonPpeMatcher(),
                        properties
                );

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(person(0.95, "worker-1"))
                );

        assertThat(result).hasSize(1);

        assertThat(result.get(0).violationType())
                .isEqualTo(ViolationType.MISSING_GLOVES);
    }

    @Test
    void preservesDifferentViolationTypesForSamePerson() {
        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        CandidateViolationEvaluator evaluator =
                new CandidateViolationEvaluator(
                        List.of(
                                new AlwaysCandidateRule(
                                        ViolationType.MISSING_GLOVES
                                ),
                                new AlwaysCandidateRule(
                                        ViolationType.MISSING_WELDING_MASK
                                )
                        ),
                        new PersonPpeMatcher(),
                        properties
                );

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(person(0.95, "worker-1"))
                );

        assertThat(result)
                .extracting(CandidateViolation::violationType)
                .containsExactlyInAnyOrder(
                        ViolationType.MISSING_GLOVES,
                        ViolationType.MISSING_WELDING_MASK
                );
    }

    @Test
    void evaluatesEachPersonSeparately() {
        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        CandidateViolationEvaluator evaluator =
                new CandidateViolationEvaluator(
                        List.of(
                                new AlwaysCandidateRule(
                                        ViolationType.MISSING_GLOVES
                                )
                        ),
                        new PersonPpeMatcher(),
                        properties
                );

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(
                                person(
                                        0.95,
                                        "worker-1",
                                        new BoundingBox(
                                                0.05,
                                                0.10,
                                                0.35,
                                                0.80
                                        )
                                ),
                                person(
                                        0.94,
                                        "worker-2",
                                        new BoundingBox(
                                                0.55,
                                                0.10,
                                                0.35,
                                                0.80
                                        )
                                )
                        )
                );

        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(CandidateViolation::personKey)
                .containsExactlyInAnyOrder(
                        "track-worker-1",
                        "track-worker-2"
                );
    }

    @Test
    void returnsImmutableCandidateList() {
        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        CandidateViolationEvaluator evaluator =
                new CandidateViolationEvaluator(
                        List.of(
                                new AlwaysCandidateRule(
                                        ViolationType.MISSING_GLOVES
                                )
                        ),
                        new PersonPpeMatcher(),
                        properties
                );

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(person(0.95, "worker-1"))
                );

        assertThatThrownBy(() ->
                result.add(result.get(0)))
                .isInstanceOf(
                        UnsupportedOperationException.class
                );
    }

    private static DetectedObject person(
            double confidence,
            String trackId
    ) {
        return person(
                confidence,
                trackId,
                new BoundingBox(
                        0.10,
                        0.10,
                        0.40,
                        0.80
                )
        );
    }

    private static DetectedObject person(
            double confidence,
            String trackId,
            BoundingBox boundingBox
    ) {
        return new DetectedObject(
                DetectionLabel.PERSON,
                "person",
                confidence,
                boundingBox,
                trackId
        );
    }

    private static DetectionFrame frame(
            DetectedObject... detections
    ) {
        return new DetectionFrame(
                EVENT_ID,
                CAMERA_ID,
                SESSION_ID,
                Instant.parse("2026-08-07T10:00:00Z"),
                "model-v1",
                25L,
                List.of(detections)
        );
    }

    private static final class AlwaysCandidateRule
            implements ViolationRule {

        private final ViolationType supportedType;

        private AlwaysCandidateRule(
                ViolationType supportedType
        ) {
            this.supportedType = supportedType;
        }

        @Override
        public ViolationType supportedType() {
            return supportedType;
        }

        @Override
        public Optional<CandidateViolation> evaluate(
                PersonContext person,
                DetectionFrame frame
        ) {
            return Optional.of(
                    new CandidateViolation(
                            frame.eventId(),
                            frame.cameraId(),
                            frame.sessionId(),
                            person.personKey(),
                            supportedType,
                            person.person().boundingBox(),
                            frame.frameTimestamp(),
                            person.person().confidence()
                    )
            );
        }
    }
}