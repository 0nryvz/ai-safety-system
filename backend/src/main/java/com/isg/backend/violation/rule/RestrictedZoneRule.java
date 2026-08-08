package com.isg.backend.violation.rule;

import com.isg.backend.violation.application.port.RestrictedZonePort;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.geometry.NormalizedPolygon;

import java.util.Optional;

public class RestrictedZoneRule implements ViolationRule {

    private final RestrictedZonePort restrictedZonePort;

    public RestrictedZoneRule(
            RestrictedZonePort restrictedZonePort
    ) {
        this.restrictedZonePort = restrictedZonePort;
    }

    @Override
    public ViolationType supportedType() {
        return ViolationType.RESTRICTED_ZONE;
    }

    @Override
    public Optional<CandidateViolation> evaluate(
            PersonContext person,
            DetectionFrame frame
    ) {
        Optional<NormalizedPolygon> zone =
                restrictedZonePort.findZone(frame.cameraId());

        if (zone.isEmpty()) {
            return Optional.empty();
        }

        BoundingBox personBox =
                person.person().boundingBox();

        double footX = personBox.footX();
        double footY = personBox.footY();

        if (!zone.orElseThrow().contains(footX, footY)) {
            return Optional.empty();
        }

        return Optional.of(
                new CandidateViolation(
                        frame.eventId(),
                        frame.cameraId(),
                        frame.sessionId(),
                        person.personKey(),
                        supportedType(),
                        personBox,
                        frame.frameTimestamp(),
                        person.person().confidence()
                )
        );
    }
}