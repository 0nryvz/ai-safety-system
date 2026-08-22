package com.isg.backend.recording.integration;

import com.isg.backend.recording.application.RecordingApplicationService;
import com.isg.backend.recording.application.RecordingCallbackCommand;
import com.isg.backend.recording.application.StartRecordingCommand;
import com.isg.backend.recording.application.StopRecordingCommand;
import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@Testcontainers
@SpringBootTest(
        properties = {
                "application.security.internal.api-key=test-internal-api-key"
        }
)
class SharedClipLifecycleIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("isg_shared_clip_lifecycle_test")
                    .withUsername("test")
                    .withPassword("test");

    @DynamicPropertySource
    static void datasourceProperties(
            DynamicPropertyRegistry registry
    ) {
        registry.add(
                "spring.datasource.url",
                postgres::getJdbcUrl
        );
        registry.add(
                "spring.datasource.username",
                postgres::getUsername
        );
        registry.add(
                "spring.datasource.password",
                postgres::getPassword
        );
    }

    @Autowired
    private RecordingApplicationService recordingApplicationService;

    @Autowired
    private RecordingRepository recordingRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private GatewayRecordingCommandPort gatewayRecordingCommandPort;

    @Test
    void sharedReadyCallbackCompletesAllLogicalViolations() {
        Fixture fixture =
                seedFixture(
                        "31",
                        "track-ready-lifecycle"
                );

        Recording leader =
                startAndStopSharedGroup(
                        fixture
                );

        recordingApplicationService.handleCallback(
                new RecordingCallbackCommand(
                        leader.id(),
                        leader.violationId(),
                        RecordingStatus.READY,
                        "recordings/shared/lifecycle-ready.mp4",
                        12_000,
                        4_096L,
                        "ready-checksum",
                        0,
                        null
                )
        );

        assertLifecycle(
                fixture.firstViolationId(),
                "COMPLETED"
        );

        assertLifecycle(
                fixture.secondViolationId(),
                "COMPLETED"
        );

        assertLifecycle(
                fixture.thirdViolationId(),
                "COMPLETED"
        );

        assertThat(
                recordingRepository
                        .findByViolationId(
                                fixture.firstViolationId()
                        )
                        .orElseThrow()
                        .status()
        ).isEqualTo(
                RecordingStatus.READY
        );

        assertThat(
                recordingRepository
                        .findByViolationId(
                                fixture.secondViolationId()
                        )
                        .orElseThrow()
                        .status()
        ).isEqualTo(
                RecordingStatus.READY
        );

        assertThat(
                recordingRepository
                        .findByViolationId(
                                fixture.thirdViolationId()
                        )
                        .orElseThrow()
                        .status()
        ).isEqualTo(
                RecordingStatus.READY
        );
    }

    @Test
    void sharedErrorCallbackErrorsAllLogicalViolations() {
        Fixture fixture =
                seedFixture(
                        "41",
                        "track-error-lifecycle"
                );

        Recording leader =
                startAndStopSharedGroup(
                        fixture
                );

        recordingApplicationService.handleCallback(
                new RecordingCallbackCommand(
                        leader.id(),
                        leader.violationId(),
                        RecordingStatus.ERROR,
                        null,
                        null,
                        null,
                        null,
                        0,
                        "ENCODE_FAILED"
                )
        );

        assertLifecycle(
                fixture.firstViolationId(),
                "ERROR"
        );

        assertLifecycle(
                fixture.secondViolationId(),
                "ERROR"
        );

        assertLifecycle(
                fixture.thirdViolationId(),
                "ERROR"
        );

        assertThat(
                recordingRepository
                        .findByViolationId(
                                fixture.firstViolationId()
                        )
                        .orElseThrow()
                        .status()
        ).isEqualTo(
                RecordingStatus.ERROR
        );

        assertThat(
                recordingRepository
                        .findByViolationId(
                                fixture.secondViolationId()
                        )
                        .orElseThrow()
                        .status()
        ).isEqualTo(
                RecordingStatus.ERROR
        );

        assertThat(
                recordingRepository
                        .findByViolationId(
                                fixture.thirdViolationId()
                        )
                        .orElseThrow()
                        .status()
        ).isEqualTo(
                RecordingStatus.ERROR
        );
    }

    private Recording startAndStopSharedGroup(
            Fixture fixture
    ) {
        Recording first =
                recordingApplicationService.start(
                        startCommand(
                                fixture.firstViolationId(),
                                fixture.cameraId()
                        )
                );

        recordingApplicationService.start(
                startCommand(
                        fixture.secondViolationId(),
                        fixture.cameraId()
                )
        );

        recordingApplicationService.start(
                startCommand(
                        fixture.thirdViolationId(),
                        fixture.cameraId()
                )
        );

        verify(
                gatewayRecordingCommandPort,
                times(1)
        ).sendStart(
                any(UUID.class),
                any(StartRecordingCommand.class)
        );

        moveToPreparing(
                fixture.firstViolationId()
        );

        recordingApplicationService.stop(
                stopCommand(
                        fixture.firstViolationId()
                )
        );

        moveToPreparing(
                fixture.secondViolationId()
        );

        recordingApplicationService.stop(
                stopCommand(
                        fixture.secondViolationId()
                )
        );

        moveToPreparing(
                fixture.thirdViolationId()
        );

        recordingApplicationService.stop(
                stopCommand(
                        fixture.thirdViolationId()
                )
        );

        verify(
                gatewayRecordingCommandPort,
                times(1)
        ).sendStop(
                any(StopRecordingCommand.class)
        );

        return recordingRepository
                .findByViolationId(
                        first.violationId()
                )
                .orElseThrow();
    }

    private Fixture seedFixture(
            String prefix,
            String subjectKey
    ) {
        UUID departmentId =
                UUID.fromString(
                        prefix + "000000-0000-4000-8000-000000000001"
                );

        UUID cameraId =
                UUID.fromString(
                        prefix + "000000-0000-4000-8000-000000000002"
                );

        UUID cameraSessionId =
                UUID.fromString(
                        prefix + "000000-0000-4000-8000-000000000003"
                );

        UUID firstViolationId =
                UUID.fromString(
                        prefix + "000000-0000-4000-8000-000000000011"
                );

        UUID secondViolationId =
                UUID.fromString(
                        prefix + "000000-0000-4000-8000-000000000012"
                );

        UUID thirdViolationId =
                UUID.fromString(
                        prefix + "000000-0000-4000-8000-000000000013"
                );

        jdbcTemplate.update(
                """
                INSERT INTO departments (
                    id,
                    code,
                    name
                )
                VALUES (?, ?, ?)
                """,
                departmentId,
                "LIFE-" + prefix,
                "Shared Lifecycle " + prefix
        );

        jdbcTemplate.update(
                """
                INSERT INTO cameras (
                    id,
                    code,
                    name,
                    department_id,
                    status
                )
                VALUES (?, ?, ?, ?, ?)
                """,
                cameraId,
                "LIFE-CAM-" + prefix,
                "Shared Lifecycle Camera " + prefix,
                departmentId,
                "OFFLINE"
        );

        jdbcTemplate.update(
                """
                INSERT INTO camera_sessions (
                    id,
                    camera_id,
                    session_id,
                    status
                )
                VALUES (?, ?, ?, ?)
                """,
                cameraSessionId,
                cameraId,
                "lifecycle-session-" + prefix,
                "ACTIVE"
        );

        insertViolation(
                firstViolationId,
                cameraId,
                departmentId,
                cameraSessionId,
                subjectKey,
                "MISSING_WELDING_MASK"
        );

        insertViolation(
                secondViolationId,
                cameraId,
                departmentId,
                cameraSessionId,
                subjectKey,
                "MISSING_GLOVES"
        );

        insertViolation(
                thirdViolationId,
                cameraId,
                departmentId,
                cameraSessionId,
                subjectKey,
                "MISSING_WELDING_APRON"
        );

        return new Fixture(
                cameraId,
                firstViolationId,
                secondViolationId,
                thirdViolationId
        );
    }

    private void insertViolation(
            UUID violationId,
            UUID cameraId,
            UUID departmentId,
            UUID cameraSessionId,
            String subjectKey,
            String violationType
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO violations (
                    id,
                    camera_id,
                    department_id,
                    camera_session_id,
                    violation_type,
                    started_at,
                    confidence,
                    model_version,
                    lifecycle_status,
                    review_status,
                    subject_key,
                    source_session_id
                )
                VALUES (
                    ?, ?, ?, ?, ?, now(),
                    0.9000,
                    'lifecycle-test-model',
                    'ACTIVE',
                    'UNREVIEWED',
                    ?,
                    ?
                )
                """,
                violationId,
                cameraId,
                departmentId,
                cameraSessionId,
                violationType,
                subjectKey,
                cameraSessionId
        );
    }

    private void moveToPreparing(
            UUID violationId
    ) {
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE violations
                        SET lifecycle_status = 'PREPARING',
                            ended_at = CURRENT_TIMESTAMP
                        WHERE id = ?
                          AND lifecycle_status = 'ACTIVE'
                        """,
                        violationId
                );

        assertThat(updated)
                .isEqualTo(1);
    }

    private void assertLifecycle(
            UUID violationId,
            String expectedStatus
    ) {
        String actual =
                jdbcTemplate.queryForObject(
                        """
                        SELECT lifecycle_status
                        FROM violations
                        WHERE id = ?
                        """,
                        String.class,
                        violationId
                );

        assertThat(actual)
                .isEqualTo(expectedStatus);
    }

    private StartRecordingCommand startCommand(
            UUID violationId,
            UUID cameraId
    ) {
        return new StartRecordingCommand(
                UUID.randomUUID(),
                violationId,
                UUID.randomUUID(),
                cameraId,
                Instant.now(),
                5,
                5,
                30
        );
    }

    private StopRecordingCommand stopCommand(
            UUID violationId
    ) {
        return new StopRecordingCommand(
                UUID.randomUUID(),
                violationId,
                Instant.now()
        );
    }

    private record Fixture(
            UUID cameraId,
            UUID firstViolationId,
            UUID secondViolationId,
            UUID thirdViolationId
    ) {
    }
}