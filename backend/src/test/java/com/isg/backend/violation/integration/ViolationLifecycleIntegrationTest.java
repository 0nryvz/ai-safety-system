package com.isg.backend.violation.integration;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.RecordingCommandPort;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationStatusKind;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationStatusHistoryRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.infrastructure.persistence.ViolationStatusHistoryJpaEntity;
import com.isg.backend.violation.service.ViolationLifecycleService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.junit.jupiter.api.AfterEach;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.sql.Timestamp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringBootTest
class ViolationLifecycleIntegrationTest {

    @Autowired
    private ViolationLifecycleService lifecycleService;

    @Autowired
    private SpringDataViolationRepository violationRepository;

    @Autowired
    private SpringDataViolationStatusHistoryRepository statusHistoryRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<UUID> testViolationIds =
            new ArrayList<>();

    private final List<UUID> testSessionIds =
            new ArrayList<>();

    private final List<UUID> testCameraIds =
            new ArrayList<>();

    private final List<UUID> testDepartmentIds =
            new ArrayList<>();

    @MockitoBean
    private CameraService cameraService;

    @MockitoBean
    private RecordingCommandPort recordingCommandPort;

    @AfterEach
    void cleanUp() {
        for (UUID violationId : testViolationIds) {
            jdbcTemplate.update(
                    "DELETE FROM violation_status_history WHERE violation_id = ?",
                    violationId
            );

            jdbcTemplate.update(
                    "DELETE FROM recordings WHERE violation_id = ?",
                    violationId
            );

            jdbcTemplate.update(
                    "DELETE FROM violations WHERE id = ?",
                    violationId
            );
        }

        for (UUID sessionRecordId : testSessionIds) {
            jdbcTemplate.update(
                    "DELETE FROM camera_sessions WHERE id = ?",
                    sessionRecordId
            );
        }

        for (UUID cameraId : testCameraIds) {
            jdbcTemplate.update(
                    "DELETE FROM cameras WHERE id = ?",
                    cameraId
            );
        }

        for (UUID departmentId : testDepartmentIds) {
            jdbcTemplate.update(
                    "DELETE FROM departments WHERE id = ?",
                    departmentId
            );
        }

        testViolationIds.clear();
        testSessionIds.clear();
        testCameraIds.clear();
        testDepartmentIds.clear();
    }

    private TestFixture createFixture() {
        UUID departmentId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionRecordId =
                UUID.randomUUID();

        UUID externalSessionId =
                UUID.randomUUID();

        String uniqueSuffix =
                UUID.randomUUID()
                        .toString()
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
                "TEST-" + uniqueSuffix,
                "Lifecycle Test Department " + uniqueSuffix,
                "Temporary integration test department"
        );

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
                "Lifecycle Test Camera " + uniqueSuffix,
                "LIFECYCLE-TEST-" + uniqueSuffix,
                departmentId
        );

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
                sessionRecordId,
                cameraId,
                Timestamp.from(Instant.now()),
                externalSessionId.toString()
        );

        testDepartmentIds.add(
                departmentId
        );

        testCameraIds.add(
                cameraId
        );

        testSessionIds.add(
                sessionRecordId
        );

        return new TestFixture(
                departmentId,
                cameraId,
                sessionRecordId,
                externalSessionId
        );
    }

    private record TestFixture(
            UUID departmentId,
            UUID cameraId,
            UUID sessionRecordId,
            UUID externalSessionId
    ) {
    }

    @Test
    void persistsLifecycleAndDeliversStartStopAfterCommit() {
        TestFixture fixture =
                createFixture();

        UUID cameraId =
                fixture.cameraId();

        UUID sessionId =
                fixture.externalSessionId();

        UUID departmentId =
                fixture.departmentId();

        Instant candidateStartedAt =
                Instant.parse(
                        "2026-08-13T12:00:00Z"
                );

        Instant confirmedAt =
                candidateStartedAt.plusSeconds(2);

        ConfirmedViolation confirmedViolation =
                new ConfirmedViolation(
                        new ViolationStateKey(
                                cameraId,
                                sessionId,
                                ViolationType.MISSING_WELDING_MASK,
                                "track-worker-1"
                        ),
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_WELDING_MASK,
                        candidateStartedAt,
                        confirmedAt,
                        0.91
                );

        ViolationJpaEntity created =
                lifecycleService.startViolation(
                        confirmedViolation,
                        "model-v1"
                );

        UUID violationId =
                created.getId();

        testViolationIds.add(
                violationId
        );

        ViolationJpaEntity persisted =
                violationRepository.findById(
                        violationId
                ).orElseThrow();

        assertThat(
                persisted.getLifecycleStatus()
        ).isEqualTo(
                ViolationLifecycleStatus.ACTIVE
        );

        assertThat(
                persisted.getCameraSessionId()
        ).isEqualTo(
                fixture.sessionRecordId()
        );

        assertThat(
                persisted.getReviewStatus()
        ).isEqualTo(
                ViolationReviewStatus.UNREVIEWED
        );

        assertThat(
                persisted.getStartedAt()
        ).isEqualTo(
                candidateStartedAt
        );

        List<ViolationStatusHistoryJpaEntity> startHistory =
                historiesFor(
                        violationId
                );

        assertThat(startHistory)
                .anySatisfy(history -> {
                    assertThat(
                            history.getStatusKind()
                    ).isEqualTo(
                            ViolationStatusKind.LIFECYCLE
                    );

                    assertThat(
                            history.getFromStatus()
                    ).isNull();

                    assertThat(
                            history.getToStatus()
                    ).isEqualTo(
                            ViolationLifecycleStatus.ACTIVE.name()
                    );
                });

        ArgumentCaptor<ViolationStartedEvent> startCaptor =
                ArgumentCaptor.forClass(
                        ViolationStartedEvent.class
                );

        verify(
                recordingCommandPort,
                times(1)
        ).startRecording(
                startCaptor.capture()
        );

        ViolationStartedEvent startEvent =
                startCaptor.getValue();

        assertThat(
                startEvent.commandId()
        ).isNotNull();

        assertThat(
                startEvent.violationId()
        ).isEqualTo(
                violationId
        );

        Instant endedAt =
                candidateStartedAt.plusSeconds(5);

        lifecycleService.endViolation(
                violationId,
                endedAt
        );

        ViolationJpaEntity ended =
                violationRepository.findById(
                        violationId
                ).orElseThrow();

        assertThat(
                ended.getEndedAt()
        ).isEqualTo(
                endedAt
        );

        ArgumentCaptor<ViolationEndedEvent> stopCaptor =
                ArgumentCaptor.forClass(
                        ViolationEndedEvent.class
                );

        verify(
                recordingCommandPort,
                times(1)
        ).stopRecording(
                stopCaptor.capture()
        );

        ViolationEndedEvent stopEvent =
                stopCaptor.getValue();

        assertThat(
                stopEvent.commandId()
        ).isNotNull();

        assertThat(
                stopEvent.violationId()
        ).isEqualTo(
                violationId
        );

        assertThat(
                stopEvent.commandId()
        ).isNotEqualTo(
                startEvent.commandId()
        );

        lifecycleService.recordingReady(
                violationId,
                endedAt.plusSeconds(2)
        );

        ViolationJpaEntity completed =
                violationRepository.findById(
                        violationId
                ).orElseThrow();

        assertThat(
                completed.getLifecycleStatus()
        ).isEqualTo(
                ViolationLifecycleStatus.COMPLETED
        );

        List<ViolationStatusHistoryJpaEntity> completedHistory =
                historiesFor(
                        violationId
                );

        assertThat(completedHistory)
                .anySatisfy(history -> {
                    assertThat(
                            history.getFromStatus()
                    ).isEqualTo(
                            ViolationLifecycleStatus.PREPARING.name()
                    );

                    assertThat(
                            history.getToStatus()
                    ).isEqualTo(
                            ViolationLifecycleStatus.COMPLETED.name()
                    );
                });

        assertThat(historiesFor(violationId))
                .anySatisfy(history -> {
                    assertThat(
                            history.getFromStatus()
                    ).isEqualTo(
                            ViolationLifecycleStatus.ACTIVE.name()
                    );

                    assertThat(
                            history.getToStatus()
                    ).isEqualTo(
                            ViolationLifecycleStatus.PREPARING.name()
                    );
                });
    }

    @Test
    void recordingErrorIsPersistedAndAudited() {
        TestFixture fixture =
                createFixture();

        UUID cameraId =
                fixture.cameraId();

        UUID sessionId =
                fixture.externalSessionId();

        UUID departmentId =
                fixture.departmentId();

        Instant startedAt =
                Instant.parse(
                        "2026-08-13T13:00:00Z"
                );

        ConfirmedViolation confirmedViolation =
                new ConfirmedViolation(
                        new ViolationStateKey(
                                cameraId,
                                sessionId,
                                ViolationType.MISSING_GLOVES,
                                "track-worker-2"
                        ),
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        startedAt.plusSeconds(2),
                        0.88
                );

        ViolationJpaEntity created =
                lifecycleService.startViolation(
                        confirmedViolation,
                        "model-v1"
                );

        UUID violationId =
                created.getId();

        testViolationIds.add(
                violationId
        );

        Instant endedAt =
                startedAt.plusSeconds(5);

        lifecycleService.endViolation(
                violationId,
                endedAt
        );

        lifecycleService.recordingError(
                violationId,
                endedAt.plusSeconds(1),
                "UPLOAD_FAILED"
        );

        ViolationJpaEntity errored =
                violationRepository.findById(
                        violationId
                ).orElseThrow();

        assertThat(
                errored.getLifecycleStatus()
        ).isEqualTo(
                ViolationLifecycleStatus.ERROR
        );

        List<ViolationStatusHistoryJpaEntity> history =
                historiesFor(
                        violationId
                );

        assertThat(history)
                .anySatisfy(item -> {
                    assertThat(
                            item.getFromStatus()
                    ).isEqualTo(
                            ViolationLifecycleStatus.ACTIVE.name()
                    );

                    assertThat(
                            item.getToStatus()
                    ).isEqualTo(
                            ViolationLifecycleStatus.PREPARING.name()
                    );
                });

        assertThat(history)
                .anySatisfy(item -> {
                    assertThat(
                            item.getFromStatus()
                    ).isEqualTo(
                            ViolationLifecycleStatus.PREPARING.name()
                    );

                    assertThat(
                            item.getToStatus()
                    ).isEqualTo(
                            ViolationLifecycleStatus.ERROR.name()
                    );

                    assertThat(
                            item.getNote()
                    ).contains(
                            "UPLOAD_FAILED"
                    );
                });
    }

    private List<ViolationStatusHistoryJpaEntity> historiesFor(
            UUID violationId
    ) {
        return statusHistoryRepository.findAll()
                .stream()
                .filter(history ->
                        history.getViolationId()
                                .equals(
                                        violationId
                                )
                )
                .toList();
    }
}