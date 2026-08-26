package com.isg.backend.violation.rule;

import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PersonPpeMatcherTest {

    private static final UUID EVENT_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CAMERA_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID SESSION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    private final PersonPpeMatcher matcher = new PersonPpeMatcher();

    @Test
    void assignsDetectionWhenItIsContainedByPerson() {
        DetectedObject person = person(
                new BoundingBox(0.1, 0.1, 0.4, 0.8),
                "person-1"
        );

        DetectedObject mask = detection(
                DetectionLabel.WELDING_MASK,
                new BoundingBox(0.2, 0.15, 0.1, 0.1)
        );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(frame(person, mask), 0.50);

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0)
                .hasDetection(DetectionLabel.WELDING_MASK))
                .isTrue();
    }

    @Test
    void assignsNonGlovesDetectionToPerson() {
        DetectedObject person =
                person(
                        new BoundingBox(
                                0.1,
                                0.1,
                                0.4,
                                0.8
                        ),
                        "person-1"
                );

        DetectedObject nonGloves =
                detection(
                        DetectionLabel.NON_GLOVES,
                        new BoundingBox(
                                0.2,
                                0.45,
                                0.1,
                                0.1
                        )
                );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(
                        frame(
                                person,
                                nonGloves
                        ),
                        0.50
                );

        assertThat(contexts)
                .hasSize(1);

        assertThat(
                contexts.get(0)
                        .hasDetection(
                                DetectionLabel.NON_GLOVES
                        )
        )
                .isTrue();
    }

    @Test
    void assignsNonWeldingMaskDetectionToPerson() {
        DetectedObject person =
                person(
                        new BoundingBox(
                                0.1,
                                0.1,
                                0.4,
                                0.8
                        ),
                        "person-1"
                );

        DetectedObject nonMask =
                detection(
                        DetectionLabel.NON_WELDING_MASK,
                        new BoundingBox(
                                0.2,
                                0.15,
                                0.1,
                                0.1
                        )
                );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(
                        frame(
                                person,
                                nonMask
                        ),
                        0.50
                );

        assertThat(
                contexts.get(0)
                        .hasDetection(
                                DetectionLabel.NON_WELDING_MASK
                        )
        )
                .isTrue();
    }

    @Test
    void assignsNonWeldingJacketDetectionToPerson() {
        DetectedObject person =
                person(
                        new BoundingBox(
                                0.1,
                                0.1,
                                0.4,
                                0.8
                        ),
                        "person-1"
                );

        DetectedObject nonJacket =
                detection(
                        DetectionLabel.NON_WELDING_JACKET,
                        new BoundingBox(
                                0.18,
                                0.22,
                                0.2,
                                0.45
                        )
                );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(
                        frame(
                                person,
                                nonJacket
                        ),
                        0.50
                );

        assertThat(
                contexts.get(0)
                        .hasDetection(
                                DetectionLabel.NON_WELDING_JACKET
                        )
        )
                .isTrue();
    }

    @Test
    void doesNotAssignDetectionWhenItIsOutsidePerson() {
        DetectedObject person = person(
                new BoundingBox(0.1, 0.1, 0.3, 0.7),
                "person-1"
        );

        DetectedObject mask = detection(
                DetectionLabel.WELDING_MASK,
                new BoundingBox(0.7, 0.1, 0.1, 0.1)
        );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(frame(person, mask), 0.50);

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0)
                .hasDetection(DetectionLabel.WELDING_MASK))
                .isFalse();
    }

    @Test
    void assignsDetectionToOnlyBestMatchingPerson() {
        DetectedObject firstPerson = person(
                new BoundingBox(0.1, 0.1, 0.4, 0.8),
                "person-1"
        );

        DetectedObject secondPerson = person(
                new BoundingBox(0.45, 0.1, 0.4, 0.8),
                "person-2"
        );

        DetectedObject gloves = detection(
                DetectionLabel.GLOVES,
                new BoundingBox(0.38, 0.4, 0.12, 0.12)
        );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(
                        frame(firstPerson, secondPerson, gloves),
                        0.20
                );

        assertThat(contexts).hasSize(2);

        long assignedPersonCount = contexts.stream()
                .filter(context ->
                        context.hasDetection(DetectionLabel.GLOVES))
                .count();

        assertThat(assignedPersonCount).isEqualTo(1);
    }

    @Test
    void doesNotAssignDetectionBelowContainmentThreshold() {
        DetectedObject person = person(
                new BoundingBox(0.1, 0.1, 0.3, 0.7),
                "person-1"
        );

        DetectedObject apron = detection(
                DetectionLabel.WELDING_APRON,
                new BoundingBox(0.35, 0.3, 0.2, 0.2)
        );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(frame(person, apron), 0.50);

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0)
                .hasDetection(DetectionLabel.WELDING_APRON))
                .isFalse();
    }

    @Test
    void usesTrackIdAsPersonKeyWhenAvailable() {
        DetectedObject person = person(
                new BoundingBox(0.1, 0.1, 0.3, 0.7),
                "worker-42"
        );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(frame(person), 0.50);

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).personKey())
                .isEqualTo("track-worker-42");
    }

    @Test
    void createsDeterministicFrameScopedPersonKeyWhenTrackIdIsMissing() {
        DetectedObject person = person(
                new BoundingBox(0.1, 0.1, 0.3, 0.7),
                null
        );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(frame(person), 0.50);

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0).personKey())
                .isEqualTo("frame-" + EVENT_ID + "-person-0");
    }

    @Test
    void treatsPersonLabelAsPersonDetection() {
        DetectedObject person = person(
                new BoundingBox(0.1, 0.1, 0.3, 0.7),
                "person-1"
        );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(frame(person), 0.50);

        assertThat(contexts).hasSize(1);
    }

    @Test
    void assignsWeldingDetectionToPerson() {
        DetectedObject person = person(
                new BoundingBox(0.1, 0.1, 0.4, 0.8),
                "person-1"
        );

        DetectedObject welding = detection(
                DetectionLabel.WELDING,
                new BoundingBox(0.2, 0.4, 0.1, 0.1)
        );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(frame(person, welding), 0.50);

        assertThat(contexts).hasSize(1);
        assertThat(contexts.get(0)
                .hasDetection(DetectionLabel.WELDING))
                .isTrue();
    }

    @Test
    void assignsWeldingJacketDetectionToPerson() {
        DetectedObject person =
                person(
                        new BoundingBox(
                                0.1,
                                0.1,
                                0.4,
                                0.8
                        ),
                        "person-1"
                );

        DetectedObject jacket =
                detection(
                        DetectionLabel.WELDING_JACKET,
                        new BoundingBox(
                                0.2,
                                0.3,
                                0.15,
                                0.3
                        )
                );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(
                        frame(
                                person,
                                jacket
                        ),
                        0.50
                );

        assertThat(contexts)
                .hasSize(1);

        assertThat(
                contexts.get(0)
                        .hasDetection(
                                DetectionLabel.WELDING_JACKET
                        )
        )
                .isTrue();
    }

    @Test
    void assignsGlovesDetectionToPerson() {
        DetectedObject person =
                person(
                        new BoundingBox(
                                0.1,
                                0.1,
                                0.4,
                                0.8
                        ),
                        "person-1"
                );

        DetectedObject gloves =
                detection(
                        DetectionLabel.GLOVES,
                        new BoundingBox(
                                0.2,
                                0.45,
                                0.1,
                                0.1
                        )
                );

        List<PersonContext> contexts =
                matcher.buildPersonContexts(
                        frame(
                                person,
                                gloves
                        ),
                        0.50
                );

        assertThat(contexts)
                .hasSize(1);

        assertThat(
                contexts.get(0)
                        .hasDetection(
                                DetectionLabel.GLOVES
                        )
        )
                .isTrue();
    }

    private static DetectedObject person(
            BoundingBox boundingBox,
            String trackId
    ) {
        return new DetectedObject(
                DetectionLabel.PERSON,
                "person",
                0.95,
                boundingBox,
                trackId
        );
    }

    private static DetectedObject detection(
            DetectionLabel label,
            BoundingBox boundingBox
    ) {
        return new DetectedObject(
                label,
                label.name().toLowerCase(),
                0.90,
                boundingBox,
                null
        );
    }

    private static DetectionFrame frame(DetectedObject... detections) {
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
}