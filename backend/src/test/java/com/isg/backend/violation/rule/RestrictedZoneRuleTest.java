package com.isg.backend.violation.rule;

import com.isg.backend.violation.application.port.RestrictedZonePort;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import com.isg.backend.violation.domain.geometry.NormalizedPoint;
import com.isg.backend.violation.domain.geometry.NormalizedPolygon;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RestrictedZoneRuleTest {

    private static final UUID EVENT_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CAMERA_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID SESSION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Test
    void supportsRestrictedZoneViolationType() {
        RestrictedZoneRule rule =
                new RestrictedZoneRule(zonePortWith(squareZone()));

        assertThat(rule.supportedType())
                .isEqualTo(ViolationType.RESTRICTED_ZONE);
    }

    @Test
    void producesCandidateWhenPersonFootPointIsInsideZone() {
        RestrictedZoneRule rule =
                new RestrictedZoneRule(zonePortWith(squareZone()));

        PersonContext person = personContext(
                new BoundingBox(0.30, 0.20, 0.20, 0.40)
        );

        Optional<CandidateViolation> result =
                rule.evaluate(person, frame());

        assertThat(result).isPresent();

        CandidateViolation candidate = result.orElseThrow();

        assertThat(candidate.violationType())
                .isEqualTo(ViolationType.RESTRICTED_ZONE);
        assertThat(candidate.personKey())
                .isEqualTo("track-worker-1");
        assertThat(candidate.eventId())
                .isEqualTo(EVENT_ID);
        assertThat(candidate.cameraId())
                .isEqualTo(CAMERA_ID);
        assertThat(candidate.sessionId())
                .isEqualTo(SESSION_ID);
        assertThat(candidate.personBox())
                .isEqualTo(person.person().boundingBox());
        assertThat(candidate.frameTimestamp())
                .isEqualTo(frame().frameTimestamp());
    }

    @Test
    void doesNotProduceCandidateWhenPersonFootPointIsOutsideZone() {
        RestrictedZoneRule rule =
                new RestrictedZoneRule(zonePortWith(squareZone()));

        PersonContext person = personContext(
                new BoundingBox(0.75, 0.20, 0.20, 0.40)
        );

        Optional<CandidateViolation> result =
                rule.evaluate(person, frame());

        assertThat(result).isEmpty();
    }

    @Test
    void producesCandidateWhenPersonFootPointIsOnZoneBoundary() {
        RestrictedZoneRule rule =
                new RestrictedZoneRule(zonePortWith(squareZone()));

        PersonContext person = personContext(
                new BoundingBox(0.40, 0.20, 0.20, 0.60)
        );

        Optional<CandidateViolation> result =
                rule.evaluate(person, frame());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().violationType())
                .isEqualTo(ViolationType.RESTRICTED_ZONE);
    }

    @Test
    void safelyProducesNoCandidateWhenCameraHasNoRestrictedZone() {
        RestrictedZonePort emptyZonePort =
                cameraId -> Optional.empty();

        RestrictedZoneRule rule =
                new RestrictedZoneRule(emptyZonePort);

        PersonContext person = personContext(
                new BoundingBox(0.30, 0.20, 0.20, 0.40)
        );

        Optional<CandidateViolation> result =
                rule.evaluate(person, frame());

        assertThat(result).isEmpty();
    }

    private static RestrictedZonePort zonePortWith(
            NormalizedPolygon zone
    ) {
        return cameraId -> Optional.of(zone);
    }

    private static NormalizedPolygon squareZone() {
        return new NormalizedPolygon(List.of(
                new NormalizedPoint(0.20, 0.20),
                new NormalizedPoint(0.80, 0.20),
                new NormalizedPoint(0.80, 0.80),
                new NormalizedPoint(0.20, 0.80)
        ));
    }

    private static PersonContext personContext(
            BoundingBox boundingBox
    ) {
        DetectedObject person = new DetectedObject(
                DetectionLabel.PERSON,
                "person",
                0.95,
                boundingBox,
                "worker-1"
        );

        return new PersonContext(
                "track-worker-1",
                person,
                List.of()
        );
    }

    private static DetectionFrame frame() {
        return new DetectionFrame(
                EVENT_ID,
                CAMERA_ID,
                SESSION_ID,
                Instant.parse("2026-08-07T10:00:00Z"),
                "model-v1",
                25L,
                List.of()
        );
    }
}