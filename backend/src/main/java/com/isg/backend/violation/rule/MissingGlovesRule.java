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

        boolean hasGloves =
                person.hasDetection(
                        DetectionLabel.GLOVES
                );

        boolean hasNonGloves =
                person.hasDetection(
                        DetectionLabel.NON_GLOVES
                );

        /*
         * Welding context yoksa PPE ihlali değerlendirilmez.
         */
        if (!isWelding) {
            return Optional.empty();
        }

        /*
         * Aynı frame'de gloves varsa güvenli kabul ediyoruz.
         * Positive class, negative class'a göre önceliklidir.
         */
        if (hasGloves) {
            return Optional.empty();
        }

        /*
         * Yeni AI contract ile non_gloves açıkça geldiğinde
         * MISSING_GLOVES adayı oluşturulur.
         */
        if (!hasNonGloves) {
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