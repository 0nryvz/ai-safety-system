package com.isg.backend.violation.rule;

import com.isg.backend.violation.config.ViolationRuleProperties;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CandidateViolationEvaluatorIntegrationTest {

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


    @Test
    void producesMissingGlovesFromRealRulePipelineWhenNonGlovesDetected() {

        CandidateViolationEvaluator evaluator =
                evaluator();

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(
                                person(),
                                welding(),
                                nonGloves()
                        )
                );

        assertThat(result)
                .extracting(
                        CandidateViolation::violationType
                )
                .contains(
                        ViolationType.MISSING_GLOVES
                );
    }


    @Test
    void producesMissingWeldingMaskFromRealRulePipelineWhenNonWeldingMaskDetected() {

        CandidateViolationEvaluator evaluator =
                evaluator();

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(
                                person(),
                                welding(),
                                nonWeldingMask()
                        )
                );

        assertThat(result)
                .extracting(
                        CandidateViolation::violationType
                )
                .contains(
                        ViolationType.MISSING_WELDING_MASK
                );
    }


    @Test
    void producesUnprotectedPersonFromRealRulePipelineWhenMultipleNegativePpeLabelsDetected() {

        CandidateViolationEvaluator evaluator =
                evaluator();

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(
                                person(),
                                welding(),
                                nonWeldingMask(),
                                nonGloves(),
                                nonWeldingJacket()
                        )
                );

        assertThat(result)
                .extracting(
                        CandidateViolation::violationType
                )
                .contains(
                        ViolationType.UNPROTECTED_PERSON
                );
    }


    @Test
    void producesMissingMaskWhenNegativeMaskDetectionExists() {

        CandidateViolationEvaluator evaluator =
                evaluator();

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(
                                person(),
                                welding(),
                                weldingMask(),
                                nonWeldingMask()
                        )
                );

        assertThat(result)
                .extracting(
                        CandidateViolation::violationType
                )
                .contains(
                        ViolationType.MISSING_WELDING_MASK
                );
    }

    private CandidateViolationEvaluator evaluator() {

        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        return new CandidateViolationEvaluator(
                List.of(
                        new MissingWeldingMaskRule(),
                        new MissingGlovesRule(),
                        new MissingWeldingApronRule(),
                        new UnprotectedPersonRule(
                                properties
                        )
                ),
                new PersonPpeMatcher(),
                properties
        );
    }


    private static DetectionFrame frame(
            DetectedObject... detections
    ) {
        return new DetectionFrame(
                EVENT_ID,
                CAMERA_ID,
                SESSION_ID,
                Instant.parse(
                        "2026-08-07T10:00:00Z"
                ),
                "model-v1",
                25L,
                List.of(
                        detections
                )
        );
    }


    private static DetectedObject person() {
        return new DetectedObject(
                DetectionLabel.PERSON,
                "person",
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
        return detection(
                DetectionLabel.WELDING,
                "welding",
                new BoundingBox(
                        0.2,
                        0.4,
                        0.1,
                        0.1
                )
        );
    }


    private static DetectedObject nonGloves() {
        return detection(
                DetectionLabel.NON_GLOVES,
                "non_gloves",
                new BoundingBox(
                        0.2,
                        0.45,
                        0.1,
                        0.1
                )
        );
    }


    private static DetectedObject weldingMask() {
        return detection(
                DetectionLabel.WELDING_MASK,
                "welding_mask",
                new BoundingBox(
                        0.2,
                        0.15,
                        0.1,
                        0.1
                )
        );
    }


    private static DetectedObject nonWeldingMask() {
        return detection(
                DetectionLabel.NON_WELDING_MASK,
                "non_welding_mask",
                new BoundingBox(
                        0.2,
                        0.15,
                        0.1,
                        0.1
                )
        );
    }


    private static DetectedObject nonWeldingJacket() {
        return detection(
                DetectionLabel.NON_WELDING_JACKET,
                "non_welding_jacket",
                new BoundingBox(
                        0.18,
                        0.22,
                        0.2,
                        0.45
                )
        );
    }


    private static DetectedObject detection(
            DetectionLabel label,
            String rawLabel,
            BoundingBox boundingBox
    ) {
        return new DetectedObject(
                label,
                rawLabel,
                0.90,
                boundingBox,
                null
        );
    }
}