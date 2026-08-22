package com.isg.backend.violation.integration;

import com.isg.backend.modules.camera.api.dto.PointDto;
import com.isg.backend.modules.camera.domain.RestrictedZone;
import com.isg.backend.modules.camera.domain.RestrictedZoneRepository;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;


@SpringBootTest
class RestrictedZoneProviderIntegrationTest {


    private static final UUID EVENT_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID CAMERA_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );


    @Autowired
    private CandidateViolationEvaluator evaluator;


    @MockitoBean
    private RestrictedZoneRepository restrictedZoneRepository;



    @Test
    void producesRestrictedZoneCandidateWhenPersonFootPointIsInsideZone() {

        RestrictedZone zone =
                restrictedZone();


        when(
                restrictedZoneRepository.findByCameraIdAndActiveTrue(
                        CAMERA_ID
                )
        )
                .thenReturn(
                        Optional.of(zone)
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



    @Test
    void doesNotProduceRestrictedZoneCandidateWhenPersonFootPointIsOutsideZone() {

        RestrictedZone zone =
                new RestrictedZone();

        zone.setCameraId(
                CAMERA_ID
        );

        zone.setName(
                "Small Zone"
        );

        zone.setActive(
                true
        );

        zone.setPolygon(
                List.of(
                        new PointDto(
                                0.10,
                                0.10
                        ),
                        new PointDto(
                                0.30,
                                0.10
                        ),
                        new PointDto(
                                0.30,
                                0.30
                        ),
                        new PointDto(
                                0.10,
                                0.30
                        )
                )
        );


        when(
                restrictedZoneRepository.findByCameraIdAndActiveTrue(
                        CAMERA_ID
                )
        )
                .thenReturn(
                        Optional.of(zone)
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
                .doesNotContain(
                        ViolationType.RESTRICTED_ZONE
                );
    }



    private RestrictedZone restrictedZone() {

        RestrictedZone zone =
                new RestrictedZone();

        zone.setCameraId(
                CAMERA_ID
        );

        zone.setName(
                "Test Zone"
        );

        zone.setActive(
                true
        );

        zone.setPolygon(
                List.of(
                        new PointDto(
                                0.10,
                                0.10
                        ),
                        new PointDto(
                                0.90,
                                0.10
                        ),
                        new PointDto(
                                0.90,
                                0.95
                        ),
                        new PointDto(
                                0.10,
                                0.95
                        )
                )
        );

        return zone;
    }



    private DetectionFrame frame(
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



    private DetectedObject person() {

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
}