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

        if (!isWelding) {
            return Optional.empty();
        }

        boolean hasNonWeldingMask =
                person.hasDetection(
                        DetectionLabel.NON_WELDING_MASK
                );

        /*
         * Explicit negative detection always wins.
         */
        if (hasNonWeldingMask) {
            return candidate(
                    person,
                    frame
            );
        }

        /*
         * A welding-mask detection is accepted as worn PPE
         * only when it belongs to this person and its center
         * is inside the person's upper/head region.
         */
        boolean hasValidWeldingMask =
                PpeSpatialRules
                        .hasWeldingMaskInHeadZone(
                                person
                        );

        if (hasValidWeldingMask) {
            return Optional.empty();
        }

        /*
         * Welding exists but no valid worn mask exists.
         */
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