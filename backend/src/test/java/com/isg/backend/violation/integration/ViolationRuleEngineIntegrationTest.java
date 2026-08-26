package com.isg.backend.violation.integration;

import com.isg.backend.violation.application.port.RestrictedZonePort;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import com.isg.backend.violation.domain.geometry.NormalizedPoint;
import com.isg.backend.violation.domain.geometry.NormalizedPolygon;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
class ViolationRuleEngineIntegrationTest {

    private static final UUID EVENT_ID =
            UUID.fromString("11111111-1111-1111-1111-111111111111");

    private static final UUID CAMERA_ID =
            UUID.fromString("22222222-2222-2222-2222-222222222222");

    private static final UUID SESSION_ID =
            UUID.fromString("33333333-3333-3333-3333-333333333333");

    @Autowired
    private CandidateViolationEvaluator evaluator;

    @MockitoBean
    private RestrictedZonePort restrictedZonePort;


    @Test
    void producesExpectedPpeCandidatesForWeldingPersonWithoutEquipment() {
        when(
                restrictedZonePort.findZone(CAMERA_ID)
        ).thenReturn(
                Optional.empty()
        );

        DetectionFrame frame =
                frame(
                        person(),
                        welding(),
                        nonWeldingMask(),
                        nonGloves(),
                        nonWeldingJacket()
                );

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame
                );

        assertThat(result)
                .extracting(
                        CandidateViolation::violationType
                )
                .contains(
                        ViolationType.MISSING_WELDING_MASK,
                        ViolationType.MISSING_GLOVES,
                        ViolationType.UNPROTECTED_PERSON
                )
                .doesNotContain(
                        ViolationType.MISSING_WELDING_APRON
                );
    }


    @Test
    void doesNotProducePpeCandidatesWhenRequiredEquipmentIsPresent() {
        when(
                restrictedZonePort.findZone(CAMERA_ID)
        ).thenReturn(
                Optional.empty()
        );

        DetectionFrame frame =
                frame(
                        person(),
                        welding(),
                        weldingMask(),
                        gloves(),
                        weldingApron(),
                        weldingJacket()
                );

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame
                );

        assertThat(result)
                .extracting(
                        CandidateViolation::violationType
                )
                .doesNotContain(
                        ViolationType.MISSING_WELDING_MASK,
                        ViolationType.MISSING_GLOVES,
                        ViolationType.MISSING_WELDING_APRON,
                        ViolationType.UNPROTECTED_PERSON
                );
    }


    @Test
    void producesRestrictedZoneCandidateUsingConfiguredPort() {
        NormalizedPolygon zone =
                new NormalizedPolygon(
                        List.of(
                                new NormalizedPoint(
                                        0.10,
                                        0.10
                                ),
                                new NormalizedPoint(
                                        0.90,
                                        0.10
                                ),
                                new NormalizedPoint(
                                        0.90,
                                        0.95
                                ),
                                new NormalizedPoint(
                                        0.10,
                                        0.95
                                )
                        )
                );

        when(
                restrictedZonePort.findZone(CAMERA_ID)
        ).thenReturn(
                Optional.of(
                        zone
                )
        );

        DetectionFrame frame =
                frame(
                        person()
                );

        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame
                );

        assertThat(result)
                .extracting(
                        CandidateViolation::violationType
                )
                .contains(
                        ViolationType.RESTRICTED_ZONE
                );
    }


    private static DetectionFrame frame(
            DetectedObject... detections
    ) {
        return new DetectionFrame(
                EVENT_ID,
                CAMERA_ID,
                SESSION_ID,
                Instant.parse(
                        "2026-08-13T00:00:00Z"
                ),
                "welding-ppe-v1",
                25L,
                List.of(
                        detections
                )
        );
    }


    private static DetectedObject person() {
        return new DetectedObject(
                DetectionLabel.PERSON,
                "Person",
                0.95,
                new BoundingBox(
                        0.20,
                        0.10,
                        0.40,
                        0.80
                ),
                "worker-1"
        );
    }


    private static DetectedObject welding() {
        return new DetectedObject(
                DetectionLabel.WELDING,
                "welding",
                0.90,
                new BoundingBox(
                        0.30,
                        0.45,
                        0.10,
                        0.10
                ),
                null
        );
    }


    private static DetectedObject weldingMask() {
        return new DetectedObject(
                DetectionLabel.WELDING_MASK,
                "welding_mask",
                0.90,
                new BoundingBox(
                        0.30,
                        0.15,
                        0.10,
                        0.10
                ),
                null
        );
    }

    private static DetectedObject nonWeldingMask() {
        return new DetectedObject(
                DetectionLabel.NON_WELDING_MASK,
                "non_welding_mask",
                0.90,
                new BoundingBox(
                        0.30,
                        0.15,
                        0.10,
                        0.10
                ),
                null
        );
    }

    private static DetectedObject gloves() {
        return new DetectedObject(
                DetectionLabel.GLOVES,
                "gloves",
                0.90,
                new BoundingBox(
                        0.30,
                        0.45,
                        0.10,
                        0.10
                ),
                null
        );
    }

    private static DetectedObject nonGloves() {
        return new DetectedObject(
                DetectionLabel.NON_GLOVES,
                "non_gloves",
                0.90,
                new BoundingBox(
                        0.30,
                        0.45,
                        0.10,
                        0.10
                ),
                null
        );
    }

    private static DetectedObject weldingApron() {
        return new DetectedObject(
                DetectionLabel.WELDING_APRON,
                "welding_apron",
                0.90,
                new BoundingBox(
                        0.28,
                        0.30,
                        0.18,
                        0.30
                ),
                null
        );
    }


    private static DetectedObject weldingJacket() {
        return new DetectedObject(
                DetectionLabel.WELDING_JACKET,
                "welding_jacket",
                0.90,
                new BoundingBox(
                        0.27,
                        0.22,
                        0.20,
                        0.40
                ),
                null
        );
    }
    private static DetectedObject nonWeldingJacket() {
        return new DetectedObject(
                DetectionLabel.NON_WELDING_JACKET,
                "non_welding_jacket",
                0.90,
                new BoundingBox(
                        0.27,
                        0.22,
                        0.20,
                        0.40
                ),
                null
        );
    }
}
