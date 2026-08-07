package com.isg.backend.violation.rule;

import com.isg.backend.violation.config.ViolationRuleProperties;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;

import java.util.List;
import java.util.Optional;

public class UnprotectedPersonRule implements ViolationRule {

    private static final int MIN_MISSING_EQUIPMENT_COUNT = 2;

    private final ViolationRuleProperties properties;

    public UnprotectedPersonRule(
            ViolationRuleProperties properties
    ) {
        this.properties = properties;
    }

    @Override
    public ViolationType supportedType() {
        return ViolationType.UNPROTECTED_PERSON;
    }

    @Override
    public Optional<CandidateViolation> evaluate(
            PersonContext person,
            DetectionFrame frame
    ) {
        if (!person.hasDetection(DetectionLabel.WELDING)) {
            return Optional.empty();
        }

        List<DetectionLabel> requiredEquipment =
                properties.getRequiredEquipmentForWelding();

        long missingEquipmentCount =
                requiredEquipment.stream()
                        .filter(label -> !person.hasDetection(label))
                        .count();

        if (missingEquipmentCount < MIN_MISSING_EQUIPMENT_COUNT) {
            return Optional.empty();
        }

        return Optional.of(
                new CandidateViolation(
                        frame.eventId(),
                        frame.cameraId(),
                        frame.sessionId(),
                        person.personKey(),
                        supportedType(),
                        person.person().boundingBox(),
                        frame.frameTimestamp(),
                        person.person().confidence()
                )
        );
    }
}