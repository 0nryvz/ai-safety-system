package com.isg.backend.violation.rule;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;

import java.util.Optional;

public class MissingWeldingMaskRule implements ViolationRule {

    @Override
    public ViolationType supportedType() {
        return ViolationType.MISSING_WELDING_MASK;
    }

    @Override
    public Optional<CandidateViolation> evaluate(
            PersonContext person,
            DetectionFrame frame
    ) {
        boolean isWelding =
                person.hasDetection(
                        DetectionLabel.WELDING
                );

        boolean hasWeldingMask =
                person.hasDetection(
                        DetectionLabel.WELDING_MASK
                );

        if (!isWelding || hasWeldingMask) {
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