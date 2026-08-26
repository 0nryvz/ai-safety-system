package com.isg.backend.violation.rule;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;

import java.util.Optional;

public class MissingGlovesRule implements ViolationRule {

    @Override
    public ViolationType supportedType() {
        return ViolationType.MISSING_GLOVES;
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

        if (!isWelding) {
            return Optional.empty();
        }

        boolean hasNonGloves =
                person.hasDetection(
                        DetectionLabel.NON_GLOVES
                );

        if (hasNonGloves) {
            return candidate(
                    person,
                    frame
            );
        }

        boolean hasValidGloves =
                PpeSpatialRules
                        .hasGlovesInWeldingZone(
                                person
                        );

        if (hasValidGloves) {
            return Optional.empty();
        }

        return candidate(
                person,
                frame
        );
    }

    private Optional<CandidateViolation> candidate(
            PersonContext person,
            DetectionFrame frame
    ) {
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