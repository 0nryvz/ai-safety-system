package com.isg.backend.recording.integration;

import com.isg.backend.recording.application.RecordingApplicationService;
import com.isg.backend.recording.application.RecordingCallbackCommand;
import com.isg.backend.recording.application.StartRecordingCommand;
import com.isg.backend.recording.application.StopRecordingCommand;
import com.isg.backend.recording.application.callback.RecordingStatusCallbackPort;
import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import com.isg.backend.reporting.infrastructure.persistence.DashboardRepository;
import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.domain.ViolationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
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
class SharedClipRecordingIntegrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("isg_shared_clip_spring_test")
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

    @Autowired
    private DashboardRepository dashboardRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @MockitoBean
    private GatewayRecordingCommandPort gatewayRecordingCommandPort;

    @MockitoBean
    private RecordingStatusCallbackPort recordingStatusCallbackPort;

    @Test
    void trackedViolationsShareOnePhysicalClipThroughRealJpaAndGroupingBridge() {
        UUID departmentId =
                UUID.fromString(
                        "11000000-0000-4000-8000-000000000001"
                );

        UUID cameraId =
                UUID.fromString(
                        "12000000-0000-4000-8000-000000000001"
                );

        UUID cameraSessionId =
                UUID.fromString(
                        "13000000-0000-4000-8000-000000000001"
                );

        UUID firstViolationId =
                UUID.fromString(
                        "14000000-0000-4000-8000-000000000001"
                );

        UUID secondViolationId =
                UUID.fromString(
                        "14000000-0000-4000-8000-000000000002"
                );

        UUID thirdViolationId =
                UUID.fromString(
                        "14000000-0000-4000-8000-000000000003"
                );

        seedContext(
                departmentId,
                cameraId,
                cameraSessionId,
                firstViolationId,
                secondViolationId,
                thirdViolationId
        );

        Recording first =
                recordingApplicationService.start(
                        startCommand(
                                firstViolationId,
                                cameraId
                        )
                );

        Recording second =
                recordingApplicationService.start(
                        startCommand(
                                secondViolationId,
                                cameraId
                        )
                );

        Recording third =
                recordingApplicationService.start(
                        startCommand(
                                thirdViolationId,
                                cameraId
                        )
                );

        assertThat(first.clipGroupId()).isNotNull();
        assertThat(second.clipGroupId())
                .isEqualTo(first.clipGroupId());
        assertThat(third.clipGroupId())
                .isEqualTo(first.clipGroupId());

        assertThat(first.startCommandId()).isNotNull();
        assertThat(second.startCommandId()).isNull();
        assertThat(third.startCommandId()).isNull();

        verify(
                gatewayRecordingCommandPort,
                times(1)
        ).sendStart(
                any(UUID.class),
                any(StartRecordingCommand.class)
        );

        var summary =
                dashboardRepository.getSummary(
                        java.util.List.of(departmentId)
                );

        assertThat(summary.todayViolationCount()).isEqualTo(3);
        assertThat(summary.last7DaysViolationCount()).isEqualTo(3);
        assertThat(summary.activeViolationCount()).isEqualTo(3);

        var distribution =
                dashboardRepository.getDistribution(
                        "TYPE",
                        java.util.List.of(departmentId)
                );

        assertThat(distribution).hasSize(3);

        assertThat(
                distribution.stream()
                        .map(item -> item.group())
                        .toList()
        ).containsExactlyInAnyOrder(
                "MISSING_WELDING_MASK",
                "MISSING_GLOVES",
                "MISSING_WELDING_APRON"
        );

        assertThat(distribution)
                .allSatisfy(
                        item ->
                                assertThat(item.count())
                                        .isEqualTo(1)
                );

        moveViolationToPreparing(firstViolationId);

        recordingApplicationService.stop(
                stopCommand(firstViolationId)
        );

        verify(
                gatewayRecordingCommandPort,
                times(0)
        ).sendStop(
                any(StopRecordingCommand.class)
        );

        moveViolationToPreparing(secondViolationId);

        recordingApplicationService.stop(
                stopCommand(secondViolationId)
        );

        verify(
                gatewayRecordingCommandPort,
                times(0)
        ).sendStop(
                any(StopRecordingCommand.class)
        );

        moveViolationToPreparing(thirdViolationId);

        recordingApplicationService.stop(
                stopCommand(thirdViolationId)
        );

        verify(
                gatewayRecordingCommandPort,
                times(1)
        ).sendStop(
                any(StopRecordingCommand.class)
        );

        Recording persistedLeader =
                recordingRepository
                        .findByViolationId(firstViolationId)
                        .orElseThrow();

        Recording persistedSecond =
                recordingRepository
                        .findByViolationId(secondViolationId)
                        .orElseThrow();

        Recording persistedThird =
                recordingRepository
                        .findByViolationId(thirdViolationId)
                        .orElseThrow();

        assertThat(persistedLeader.stopCommandId())
                .isNotNull();

        assertThat(persistedSecond.stopCommandId())
                .isNull();

        assertThat(persistedThird.stopCommandId())
                .isNull();

        recordingApplicationService.handleCallback(
                new RecordingCallbackCommand(
                        persistedLeader.id(),
                        firstViolationId,
                        RecordingStatus.READY,
                        "recordings/shared/integration-track.mp4",
                        12_000,
                        4_096L,
                        "integration-checksum",
                        0,
                        null
                )
        );

        Recording readyFirst =
                recordingRepository
                        .findByViolationId(firstViolationId)
                        .orElseThrow();

        Recording readySecond =
                recordingRepository
                        .findByViolationId(secondViolationId)
                        .orElseThrow();

        Recording readyThird =
                recordingRepository
                        .findByViolationId(thirdViolationId)
                        .orElseThrow();

        assertThat(readyFirst.status())
                .isEqualTo(RecordingStatus.READY);
        assertThat(readySecond.status())
                .isEqualTo(RecordingStatus.READY);
        assertThat(readyThird.status())
                .isEqualTo(RecordingStatus.READY);

        assertThat(readyFirst.objectKey())
                .isEqualTo("recordings/shared/integration-track.mp4");

        assertThat(readySecond.objectKey())
                .isEqualTo(readyFirst.objectKey());

        assertThat(readyThird.objectKey())
                .isEqualTo(readyFirst.objectKey());

        assertThat(readySecond.readyAt())
                .isEqualTo(readyFirst.readyAt());

        assertThat(readyThird.readyAt())
                .isEqualTo(readyFirst.readyAt());

        verify(
                recordingStatusCallbackPort,
                times(3)
        ).publish(any());
    }

    @Test
    void transactionalViolationEventsReachSharedRecordingFlowAfterCommit() {
        UUID departmentId =
                UUID.fromString(
                        "21000000-0000-4000-8000-000000000001"
                );

        UUID cameraId =
                UUID.fromString(
                        "22000000-0000-4000-8000-000000000001"
                );

        UUID cameraSessionId =
                UUID.fromString(
                        "23000000-0000-4000-8000-000000000001"
                );

        UUID firstViolationId =
                UUID.fromString(
                        "24000000-0000-4000-8000-000000000001"
                );

        UUID secondViolationId =
                UUID.fromString(
                        "24000000-0000-4000-8000-000000000002"
                );

        UUID thirdViolationId =
                UUID.fromString(
                        "24000000-0000-4000-8000-000000000003"
                );

        seedContext(
                departmentId,
                cameraId,
                cameraSessionId,
                firstViolationId,
                secondViolationId,
                thirdViolationId
        );

        Instant startedAt =
                Instant.now();

        publishAfterCommit(
                new ViolationStartedEvent(
                        UUID.randomUUID(),
                        firstViolationId,
                        cameraId,
                        cameraSessionId,
                        ViolationType.MISSING_WELDING_MASK,
                        startedAt,
                        startedAt
                )
        );

        publishAfterCommit(
                new ViolationStartedEvent(
                        UUID.randomUUID(),
                        secondViolationId,
                        cameraId,
                        cameraSessionId,
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        startedAt
                )
        );

        publishAfterCommit(
                new ViolationStartedEvent(
                        UUID.randomUUID(),
                        thirdViolationId,
                        cameraId,
                        cameraSessionId,
                        ViolationType.MISSING_WELDING_APRON,
                        startedAt,
                        startedAt
                )
        );

        assertThat(
                recordingRepository
                        .findByViolationId(firstViolationId)
        ).isPresent();

        assertThat(
                recordingRepository
                        .findByViolationId(secondViolationId)
        ).isPresent();

        assertThat(
                recordingRepository
                        .findByViolationId(thirdViolationId)
        ).isPresent();

        UUID clipGroupId =
                recordingRepository
                        .findByViolationId(firstViolationId)
                        .orElseThrow()
                        .clipGroupId();

        assertThat(clipGroupId).isNotNull();

        assertThat(
                recordingRepository
                        .findByViolationId(secondViolationId)
                        .orElseThrow()
                        .clipGroupId()
        ).isEqualTo(clipGroupId);

        assertThat(
                recordingRepository
                        .findByViolationId(thirdViolationId)
                        .orElseThrow()
                        .clipGroupId()
        ).isEqualTo(clipGroupId);

        verify(
                gatewayRecordingCommandPort,
                times(1)
        ).sendStart(
                any(UUID.class),
                any(StartRecordingCommand.class)
        );

        endViolationAndPublishAfterCommit(
                firstViolationId,
                startedAt.plusSeconds(10)
        );

        verify(
                gatewayRecordingCommandPort,
                times(0)
        ).sendStop(
                any(StopRecordingCommand.class)
        );

        endViolationAndPublishAfterCommit(
                secondViolationId,
                startedAt.plusSeconds(11)
        );

        verify(
                gatewayRecordingCommandPort,
                times(0)
        ).sendStop(
                any(StopRecordingCommand.class)
        );

        endViolationAndPublishAfterCommit(
                thirdViolationId,
                startedAt.plusSeconds(12)
        );

        verify(
                gatewayRecordingCommandPort,
                times(1)
        ).sendStop(
                any(StopRecordingCommand.class)
        );
    }

    private void publishAfterCommit(
            Object event
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.executeWithoutResult(
                status ->
                        eventPublisher.publishEvent(
                                event
                        )
        );
    }

    private void endViolationAndPublishAfterCommit(
            UUID violationId,
            Instant endedAt
    ) {
        TransactionTemplate transactionTemplate =
                new TransactionTemplate(
                        transactionManager
                );

        transactionTemplate.executeWithoutResult(
                status -> {
                    int updated =
                            jdbcTemplate.update(
                                    """
                                    UPDATE violations
                                    SET lifecycle_status = 'PREPARING',
                                        ended_at = ?
                                    WHERE id = ?
                                      AND lifecycle_status = 'ACTIVE'
                                    """,
                                    java.sql.Timestamp.from(endedAt),
                                    violationId
                            );

                    assertThat(updated)
                            .isEqualTo(1);

                    eventPublisher.publishEvent(
                            new ViolationEndedEvent(
                                    UUID.randomUUID(),
                                    violationId,
                                    endedAt
                            )
                    );
                }
        );
    }
    private void seedContext(
            UUID departmentId,
            UUID cameraId,
            UUID cameraSessionId,
            UUID firstViolationId,
            UUID secondViolationId,
            UUID thirdViolationId
    ) {
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
                "SPRING-" + departmentId.toString().substring(0, 8),
                "Spring Shared Clip Test " + departmentId.toString().substring(0, 8)
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
                "CAM-" + cameraId.toString().substring(0, 8),
                "Spring Shared Clip Camera " + cameraId.toString().substring(0, 8),
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
                "session-" + cameraSessionId.toString().substring(0, 8),
                "ACTIVE"
        );

        insertViolation(
                firstViolationId,
                cameraId,
                departmentId,
                cameraSessionId,
                "MISSING_WELDING_MASK"
        );

        insertViolation(
                secondViolationId,
                cameraId,
                departmentId,
                cameraSessionId,
                "MISSING_GLOVES"
        );

        insertViolation(
                thirdViolationId,
                cameraId,
                departmentId,
                cameraSessionId,
                "MISSING_WELDING_APRON"
        );
    }

    private void insertViolation(
            UUID violationId,
            UUID cameraId,
            UUID departmentId,
            UUID cameraSessionId,
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
                    'integration-model',
                    'ACTIVE',
                    'UNREVIEWED',
                    'track-77',
                    ?
                )
                """,
                violationId,
                cameraId,
                departmentId,
                cameraSessionId,
                violationType,
                cameraSessionId
        );
    }

    private void moveViolationToPreparing(
            UUID violationId
    ) {
        int updated =
                jdbcTemplate.update(
                        """
                        UPDATE violations
                        SET lifecycle_status = 'PREPARING',
                            ended_at = now()
                        WHERE id = ?
                        """,
                        violationId
                );

        assertThat(updated).isEqualTo(1);
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
                Instant.parse(
                        "2026-01-01T10:00:00Z"
                ),
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
                Instant.parse(
                        "2026-01-01T10:00:10Z"
                )
        );
    }
}