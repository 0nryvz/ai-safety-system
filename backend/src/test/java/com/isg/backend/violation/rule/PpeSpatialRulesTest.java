package com.isg.backend.violation.rule;

import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PpeSpatialRulesTest {

    @Test
    void gloveInsidePersonButFarFromWeldingIsNotAccepted() {
        PersonContext person = context(
                new BoundingBox(0.10, 0.10, 0.60, 0.80),
                new BoundingBox(0.42, 0.58, 0.10, 0.10),
                new BoundingBox(0.12, 0.18, 0.08, 0.08)
        );

        assertThat(
                PpeSpatialRules.hasGlovesInWeldingZone(person)
        ).isFalse();
    }

    @Test
    void gloveNearWeldingIsAccepted() {
        PersonContext person = context(
                new BoundingBox(0.10, 0.10, 0.60, 0.80),
                new BoundingBox(0.42, 0.58, 0.10, 0.10),
                new BoundingBox(0.34, 0.52, 0.08, 0.08)
        );

        assertThat(
                PpeSpatialRules.hasGlovesInWeldingZone(person)
        ).isTrue();
    }

    @Test
    void weldingGloveDecisionIsScaleInvariant() {
        PersonContext largePerson = context(
                new BoundingBox(0.10, 0.10, 0.60, 0.80),
                new BoundingBox(0.42, 0.58, 0.10, 0.10),
                new BoundingBox(0.34, 0.52, 0.08, 0.08)
        );

        PersonContext smallPerson = context(
                new BoundingBox(0.10, 0.10, 0.30, 0.40),
                new BoundingBox(0.26, 0.34, 0.05, 0.05),
                new BoundingBox(0.22, 0.30, 0.04, 0.04)
        );

        assertThat(
                PpeSpatialRules.hasGlovesInWeldingZone(largePerson)
        ).isTrue();

        assertThat(
                PpeSpatialRules.hasGlovesInWeldingZone(smallPerson)
        ).isTrue();
    }

    @Test
    void weldingMaskMustBeInPersonHeadZone() {
        DetectedObject personDetection = detection(
                DetectionLabel.PERSON,
                new BoundingBox(0.10, 0.10, 0.60, 0.80)
        );

        DetectedObject validMask = detection(
                DetectionLabel.WELDING_MASK,
                new BoundingBox(0.30, 0.16, 0.12, 0.12)
        );

        PersonContext validContext =
                new PersonContext(
                        "worker-1",
                        personDetection,
                        List.of(validMask)
                );

        assertThat(
                PpeSpatialRules.hasWeldingMaskInHeadZone(
                        validContext
                )
        ).isTrue();

        DetectedObject torsoMask = detection(
                DetectionLabel.WELDING_MASK,
                new BoundingBox(0.30, 0.55, 0.12, 0.12)
        );

        PersonContext invalidContext =
                new PersonContext(
                        "worker-1",
                        personDetection,
                        List.of(torsoMask)
                );

        assertThat(
                PpeSpatialRules.hasWeldingMaskInHeadZone(
                        invalidContext
                )
        ).isFalse();
    }

    private PersonContext context(
            BoundingBox personBox,
            BoundingBox weldingBox,
            BoundingBox gloveBox
    ) {
        return new PersonContext(
                "worker-1",
                detection(
                        DetectionLabel.PERSON,
                        personBox
                ),
                List.of(
                        detection(
                                DetectionLabel.WELDING,
                                weldingBox
                        ),
                        detection(
                                DetectionLabel.GLOVES,
                                gloveBox
                        )
                )
        );
    }

    private DetectedObject detection(
            DetectionLabel label,
            BoundingBox box
    ) {
        return new DetectedObject(
                label,
                label.name().toLowerCase(),
                0.90,
                box,
                null
        );
    }
}