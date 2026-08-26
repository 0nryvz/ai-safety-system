package com.isg.backend.violation.integration;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.query.ViolationReviewCommand;
import com.isg.backend.violation.query.ViolationReviewResponse;
import com.isg.backend.violation.service.ViolationReviewService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;


@SpringBootTest
class ViolationReviewConcurrencyIntegrationTest {

    @Autowired
    private ViolationReviewService reviewService;

    @Autowired
    private SpringDataViolationRepository violationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;


    @MockitoBean
    private AuthorizationService authorizationService;


    private final List<UUID> violationIds =
            new ArrayList<>();

    private final List<UUID> cameraIds =
            new ArrayList<>();

    private final List<UUID> departmentIds =
            new ArrayList<>();

    private final List<UUID> sessionIds =
            new ArrayList<>();

    private final List<UUID> userIds =
            new ArrayList<>();


    @AfterEach
    void cleanup() {

        for (UUID violationId : violationIds) {

            jdbcTemplate.update(
                    """
                    DELETE FROM violation_status_history
                    WHERE violation_id = ?
                    """,
                    violationId
            );

            jdbcTemplate.update(
                    """
                    DELETE FROM violations
                    WHERE id = ?
                    """,
                    violationId
            );
        }


        for (UUID sessionId : sessionIds) {

            jdbcTemplate.update(
                    """
                    DELETE FROM camera_sessions
                    WHERE id = ?
                    """,
                    sessionId
            );
        }


        for (UUID cameraId : cameraIds) {

            jdbcTemplate.update(
                    """
                    DELETE FROM cameras
                    WHERE id = ?
                    """,
                    cameraId
            );
        }


        for (UUID userId : userIds) {

            jdbcTemplate.update(
                    """
                    DELETE FROM users
                    WHERE id = ?
                    """,
                    userId
            );
        }


        for (UUID departmentId : departmentIds) {

            jdbcTemplate.update(
                    """
                    DELETE FROM departments
                    WHERE id = ?
                    """,
                    departmentId
            );
        }
    }


    @Test
    void returnsIncrementedVersionAfterSuccessfulReviewUpdate() {

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();


        userIds.add(
                reviewerId
        );

        insertUser(
                reviewerId
        );


        departmentIds.add(
                departmentId
        );

        cameraIds.add(
                cameraId
        );

        sessionIds.add(
                sessionId
        );


        insertDepartment(
                departmentId
        );

        insertCamera(
                cameraId,
                departmentId
        );

        insertCameraSession(
                sessionId,
                cameraId
        );


        ViolationJpaEntity violation =
                new ViolationJpaEntity(
                        violationId,
                        cameraId,
                        departmentId,
                        sessionId,
                        null,
                        ViolationType.MISSING_GLOVES,
                        Instant.now(),
                        BigDecimal.valueOf(0.95),
                        "model-v1",
                        ViolationLifecycleStatus.ACTIVE,
                        ViolationReviewStatus.UNREVIEWED,
                        Instant.now(),
                        "worker-1",
                        sessionId
                );


        violationRepository.saveAndFlush(
                violation
        );


        violationIds.add(
                violationId
        );


        long initialVersion =
                violationRepository.findById(
                                violationId
                        )
                        .orElseThrow()
                        .getVersion();


        when(
                authorizationService.canAccessDepartment(
                        reviewerId,
                        departmentId
                )
        )
                .thenReturn(true);


        ViolationReviewResponse response =
                reviewService.review(
                        new ViolationReviewCommand(
                                violationId,
                                ViolationReviewStatus.CONFIRMED,
                                reviewerId,
                                initialVersion
                        )
                );


        assertThat(
                response.version()
        )
                .isGreaterThan(
                        initialVersion
                );


        long databaseVersion =
                violationRepository.findById(
                                violationId
                        )
                        .orElseThrow()
                        .getVersion();


        assertThat(
                databaseVersion
        )
                .isEqualTo(
                        response.version()
                );
    }


    private void insertDepartment(
            UUID departmentId
    ) {

        String suffix =
                departmentId.toString()
                        .substring(0, 8);

        jdbcTemplate.update(
                """
                INSERT INTO departments (
                    id,
                    code,
                    name,
                    description,
                    active
                )
                VALUES (?, ?, ?, ?, true)
                """,
                departmentId,
                "DEP-" + suffix,
                "TEST-DEPARTMENT-" + suffix,
                "Concurrency test department"
        );
    }


    private void insertCamera(
            UUID cameraId,
            UUID departmentId
    ) {

        String suffix =
                cameraId.toString()
                        .substring(0, 8);

        jdbcTemplate.update(
                """
                INSERT INTO cameras (
                    id,
                    name,
                    code,
                    department_id,
                    status,
                    active
                )
                VALUES (?, ?, ?, ?, 'ONLINE', true)
                """,
                cameraId,
                "TEST-CAMERA-" + suffix,
                "CAM-" + suffix,
                departmentId
        );
    }


    private void insertCameraSession(
            UUID sessionId,
            UUID cameraId
    ) {

        jdbcTemplate.update(
                """
                INSERT INTO camera_sessions (
                    id,
                    camera_id,
                    status,
                    started_at,
                    session_id
                )
                VALUES (?, ?, 'ACTIVE', ?, ?)
                """,
                sessionId,
                cameraId,
                Timestamp.from(
                        Instant.now()
                ),
                sessionId.toString()
        );
    }


    private void insertUser(
            UUID userId
    ) {

        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id,
                    email,
                    password_hash,
                    full_name,
                    active
                )
                VALUES (?, ?, ?, ?, true)
                """,
                userId,
                "reviewer-" + userId + "@test.com",
                "$2a$10$abcdefghijklmnopqrstuv",
                "Test Reviewer"
        );
    }


    @Test
    void staleJpaUpdateThrowsOptimisticLockException() {

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();


        violationIds.add(
                violationId
        );

        cameraIds.add(
                cameraId
        );

        departmentIds.add(
                departmentId
        );

        sessionIds.add(
                sessionId
        );

        userIds.add(
                reviewerId
        );


        insertDepartment(
                departmentId
        );

        insertCamera(
                cameraId,
                departmentId
        );

        insertCameraSession(
                sessionId,
                cameraId
        );

        insertUser(
                reviewerId
        );


        ViolationJpaEntity violation =
                new ViolationJpaEntity(
                        violationId,
                        cameraId,
                        departmentId,
                        sessionId,
                        null,
                        ViolationType.MISSING_GLOVES,
                        Instant.now(),
                        BigDecimal.valueOf(0.95),
                        "model-v1",
                        ViolationLifecycleStatus.ACTIVE,
                        ViolationReviewStatus.UNREVIEWED,
                        Instant.now(),
                        "worker-1",
                        sessionId
                );


        violationRepository.saveAndFlush(
                violation
        );


        ViolationJpaEntity firstTransactionCopy =
                violationRepository.findById(
                                violationId
                        )
                        .orElseThrow();


        ViolationJpaEntity secondTransactionCopy =
                violationRepository.findById(
                                violationId
                        )
                        .orElseThrow();


        firstTransactionCopy.review(
                ViolationReviewStatus.CONFIRMED,
                reviewerId,
                Instant.now()
        );


        violationRepository.saveAndFlush(
                firstTransactionCopy
        );


        secondTransactionCopy.review(
                ViolationReviewStatus.FALSE_ALARM,
                reviewerId,
                Instant.now()
        );


        assertThatThrownBy(
                () ->
                        violationRepository.saveAndFlush(
                                secondTransactionCopy
                        )
        )
                .isInstanceOf(
                        ObjectOptimisticLockingFailureException.class
                );
    }
}