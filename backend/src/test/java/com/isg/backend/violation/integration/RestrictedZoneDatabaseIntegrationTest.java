package com.isg.backend.violation.integration;

import com.isg.backend.modules.camera.domain.RestrictedZone;
import com.isg.backend.modules.camera.domain.RestrictedZoneRepository;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@Transactional
class RestrictedZoneDatabaseIntegrationTest {

    private static final UUID CAMERA_ID =
            UUID.fromString(
                    "22222222-2222-2222-2222-222222222222"
            );

    private static final UUID DEPARTMENT_ID =
            UUID.fromString(
                    "44444444-4444-4444-4444-444444444444"
            );

    private static final UUID EVENT_ID =
            UUID.fromString(
                    "11111111-1111-1111-1111-111111111111"
            );

    private static final UUID SESSION_ID =
            UUID.fromString(
                    "33333333-3333-3333-3333-333333333333"
            );


    @Autowired
    private CandidateViolationEvaluator evaluator;


    @Autowired
    private RestrictedZoneRepository restrictedZoneRepository;


    @Autowired
    private JdbcTemplate jdbcTemplate;


    @BeforeEach
    void setUp() {

        jdbcTemplate.update(
                """
                INSERT INTO departments (
                    id,
                    code,
                    name,
                    description,
                    active,
                    version
                )
                VALUES (?, ?, ?, ?, true, 0)
                """,
                DEPARTMENT_ID,
                "TEST-DEPT",
                "Test Department",
                "Restricted zone integration test"
        );


        jdbcTemplate.update(
                """
                        INSERT INTO cameras (
                            id,
                            name,
                            code,
                            department_id,
                            status,
                            active,
                            version
                        )
                        VALUES (?, ?, ?, ?, 'ONLINE', true, 0)
                """,
                CAMERA_ID,
                "Restricted Zone Test Camera",
                "CAM-RESTRICTED-TEST",
                DEPARTMENT_ID
        );
    }


    @Test
    void producesRestrictedZoneCandidateWhenFootPointIsInsideDatabaseZone() {

        RestrictedZone zone =
                new RestrictedZone();

        zone.setCameraId(
                CAMERA_ID
        );

        zone.setName(
                "DB Test Zone"
        );

        zone.setPolygon(
                List.of(
                        point(0.10, 0.10),
                        point(0.90, 0.10),
                        point(0.90, 0.95),
                        point(0.10, 0.95)
                )
        );

        zone.setActive(true);


        restrictedZoneRepository.save(
                zone
        );


        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(
                                person()
                        )
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
    void doesNotProduceRestrictedZoneCandidateWhenFootPointIsOutsideDatabaseZone() {

        RestrictedZone zone =
                new RestrictedZone();

        zone.setCameraId(
                CAMERA_ID
        );

        zone.setName(
                "Small DB Zone"
        );

        zone.setPolygon(
                List.of(
                        point(0.10, 0.10),
                        point(0.30, 0.10),
                        point(0.30, 0.30),
                        point(0.10, 0.30)
                )
        );

        zone.setActive(true);


        restrictedZoneRepository.save(
                zone
        );


        List<CandidateViolation> result =
                evaluator.evaluate(
                        frame(
                                person()
                        )
                );


        assertThat(result)
                .extracting(
                        CandidateViolation::violationType
                )
                .doesNotContain(
                        ViolationType.RESTRICTED_ZONE
                );
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


    private com.isg.backend.modules.camera.api.dto.PointDto point(
            double x,
            double y
    ) {
        return new com.isg.backend.modules.camera.api.dto.PointDto(
                x,
                y
        );
    }
}