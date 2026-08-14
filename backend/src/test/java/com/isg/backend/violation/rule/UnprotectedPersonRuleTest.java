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
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UnprotectedPersonRuleTest {

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
    void supportsUnprotectedPersonViolationType() {
        UnprotectedPersonRule rule =
                new UnprotectedPersonRule(
                        properties()
                );

        assertThat(
                rule.supportedType()
        ).isEqualTo(
                ViolationType.UNPROTECTED_PERSON
        );
    }

    @Test
    void producesCandidateWhenConfiguredMinimumEquipmentCountIsMissing() {
        UnprotectedPersonRule rule =
                new UnprotectedPersonRule(
                        properties()
                );

        PersonContext person =
                personContext(
                        welding(),
                        mask()
                );

        Optional<CandidateViolation> result =
                rule.evaluate(
                        person,
                        frame()
                );

        assertThat(result)
                .isPresent();

        CandidateViolation candidate =
                result.orElseThrow();

        assertThat(candidate.violationType())
                .isEqualTo(
                        ViolationType.UNPROTECTED_PERSON
                );

        assertThat(candidate.personKey())
                .isEqualTo(
                        "track-worker-1"
                );

        assertThat(candidate.eventId())
                .isEqualTo(
                        EVENT_ID
                );

        assertThat(candidate.cameraId())
                .isEqualTo(
                        CAMERA_ID
                );

        assertThat(candidate.sessionId())
                .isEqualTo(
                        SESSION_ID
                );

        assertThat(candidate.personBox())
                .isEqualTo(
                        person.person()
                                .boundingBox()
                );

        assertThat(candidate.frameTimestamp())
                .isEqualTo(
                        frame().frameTimestamp()
                );
    }

    @Test
    void doesNotProduceCandidateBelowConfiguredMinimumMissingCount() {
        UnprotectedPersonRule rule =
                new UnprotectedPersonRule(
                        properties()
                );

        PersonContext person =
                personContext(
                        welding(),
                        mask(),
                        gloves(),
                        apron()
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
    void doesNotProduceCandidateWhenAllRequiredEquipmentIsPresent() {
        UnprotectedPersonRule rule =
                new UnprotectedPersonRule(
                        properties()
                );

        PersonContext person =
                personContext(
                        welding(),
                        mask(),
                        gloves(),
                        apron(),
                        jacket()
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
        UnprotectedPersonRule rule =
                new UnprotectedPersonRule(
                        properties()
                );

        PersonContext person =
                personContext(
                        mask()
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
    void respectsConfiguredRequiredEquipmentList() {
        ViolationRuleProperties properties =
                properties();

        properties.setRequiredEquipmentForWelding(
                List.of(
                        DetectionLabel.WELDING_MASK,
                        DetectionLabel.GLOVES
                )
        );

        UnprotectedPersonRule rule =
                new UnprotectedPersonRule(
                        properties
                );

        PersonContext person =
                personContext(
                        welding()
                );

        Optional<CandidateViolation> result =
                rule.evaluate(
                        person,
                        frame()
                );

        assertThat(result)
                .isPresent();
    }

    @Test
    void respectsConfiguredMinimumMissingEquipmentCount() {
        ViolationRuleProperties properties =
                properties();

        properties.setMinimumMissingEquipmentForUnprotectedPerson(
                3
        );

        UnprotectedPersonRule rule =
                new UnprotectedPersonRule(
                        properties
                );

        PersonContext onlyTwoMissing =
                personContext(
                        welding(),
                        mask(),
                        gloves()
                );

        assertThat(
                rule.evaluate(
                        onlyTwoMissing,
                        frame()
                )
        ).isEmpty();

        PersonContext threeMissing =
                personContext(
                        welding(),
                        mask()
                );

        assertThat(
                rule.evaluate(
                        threeMissing,
                        frame()
                )
        ).isPresent();
    }

    private static ViolationRuleProperties properties() {
        return new ViolationRuleProperties();
    }

    private static PersonContext personContext(
            DetectedObject... associatedDetections
    ) {
        DetectedObject person =
                new DetectedObject(
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

        return new PersonContext(
                "track-worker-1",
                person,
                List.of(
                        associatedDetections
                )
        );
    }

    private static DetectedObject welding() {
        return detection(
                DetectionLabel.WELDING,
                new BoundingBox(
                        0.2,
                        0.4,
                        0.1,
                        0.1
                )
        );
    }

    private static DetectedObject mask() {
        return detection(
                DetectionLabel.WELDING_MASK,
                new BoundingBox(
                        0.2,
                        0.15,
                        0.1,
                        0.1
                )
        );
    }

    private static DetectedObject gloves() {
        return detection(
                DetectionLabel.GLOVES,
                new BoundingBox(
                        0.2,
                        0.45,
                        0.1,
                        0.1
                )
        );
    }

    private static DetectedObject apron() {
        return detection(
                DetectionLabel.WELDING_APRON,
                new BoundingBox(
                        0.2,
                        0.3,
                        0.15,
                        0.3
                )
        );
    }

    private static DetectedObject jacket() {
        return detection(
                DetectionLabel.WELDING_JACKET,
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
            BoundingBox boundingBox
    ) {
        return new DetectedObject(
                label,
                label.name()
                        .toLowerCase(),
                0.90,
                boundingBox,
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