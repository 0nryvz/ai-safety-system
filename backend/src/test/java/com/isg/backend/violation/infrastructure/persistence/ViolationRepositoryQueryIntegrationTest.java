package com.isg.backend.violation.infrastructure.persistence;

import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.query.ViolationQueryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class ViolationRepositoryQueryIntegrationTest {

    @Autowired
    private SpringDataViolationRepository violationRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID departmentA;
    private UUID departmentB;
    private UUID cameraA;
    private UUID cameraB;

    private UUID violationA;
    private UUID violationB;
    private UUID violationC;

    @BeforeEach
    void setUp() {
        departmentA =
                UUID.randomUUID();

        departmentB =
                UUID.randomUUID();

        cameraA =
                UUID.randomUUID();

        cameraB =
                UUID.randomUUID();

        violationA =
                UUID.randomUUID();

        violationB =
                UUID.randomUUID();

        violationC =
                UUID.randomUUID();

        insertDepartment(
                departmentA,
                "D-A-" + UUID.randomUUID()
                        .toString()
                        .substring(
                                0,
                                8
                        ),
                "Department A"
        );

        insertDepartment(
                departmentB,
                "D-B-" + UUID.randomUUID()
                        .toString()
                        .substring(
                                0,
                                8
                        ),
                "Department B"
        );

        insertCamera(
                cameraA,
                departmentA,
                "CAM-" + UUID.randomUUID(),
                "Camera A"
        );

        insertCamera(
                cameraB,
                departmentB,
                "CAM-" + UUID.randomUUID(),
                "Camera B"
        );

        insertViolation(
                violationA,
                cameraA,
                departmentA,
                ViolationType.MISSING_WELDING_MASK,
                Instant.parse(
                        "2026-08-11T10:00:00Z"
                ),
                ViolationLifecycleStatus.ACTIVE,
                ViolationReviewStatus.UNREVIEWED
        );

        insertViolation(
                violationB,
                cameraA,
                departmentA,
                ViolationType.MISSING_GLOVES,
                Instant.parse(
                        "2026-08-11T11:00:00Z"
                ),
                ViolationLifecycleStatus.ACTIVE,
                ViolationReviewStatus.CONFIRMED
        );

        insertViolation(
                violationC,
                cameraB,
                departmentB,
                ViolationType.MISSING_WELDING_MASK,
                Instant.parse(
                        "2026-08-11T12:00:00Z"
                ),
                ViolationLifecycleStatus.ACTIVE,
                ViolationReviewStatus.UNREVIEWED
        );
    }

    @Test
    void appliesEverySupportedFilterAndCombinedFilters() {
        assertThat(
                find(
                        new ViolationQueryFilter(
                                Instant.parse(
                                        "2026-08-11T10:30:00Z"
                                ),
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        List.of(
                                departmentA,
                                departmentB
                        )
                )
        )
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactlyInAnyOrder(
                        violationB,
                        violationC
                );

        assertThat(
                find(
                        new ViolationQueryFilter(
                                null,
                                Instant.parse(
                                        "2026-08-11T11:30:00Z"
                                ),
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        List.of(
                                departmentA,
                                departmentB
                        )
                )
        )
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactlyInAnyOrder(
                        violationA,
                        violationB
                );

        assertThat(
                find(
                        new ViolationQueryFilter(
                                null,
                                null,
                                ViolationType.MISSING_WELDING_MASK,
                                null,
                                null,
                                null,
                                null
                        ),
                        List.of(
                                departmentA,
                                departmentB
                        )
                )
        )
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactlyInAnyOrder(
                        violationA,
                        violationC
                );

        assertThat(
                find(
                        new ViolationQueryFilter(
                                null,
                                null,
                                null,
                                cameraA,
                                null,
                                null,
                                null
                        ),
                        List.of(
                                departmentA,
                                departmentB
                        )
                )
        )
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactlyInAnyOrder(
                        violationA,
                        violationB
                );

        assertThat(
                find(
                        new ViolationQueryFilter(
                                null,
                                null,
                                null,
                                null,
                                departmentB,
                                null,
                                null
                        ),
                        List.of(
                                departmentA,
                                departmentB
                        )
                )
        )
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactly(
                        violationC
                );

        assertThat(
                find(
                        new ViolationQueryFilter(
                                null,
                                null,
                                null,
                                null,
                                null,
                                ViolationLifecycleStatus.ACTIVE,
                                null
                        ),
                        List.of(
                                departmentA,
                                departmentB
                        )
                )
        )
                .hasSize(
                        3
                );

        assertThat(
                find(
                        new ViolationQueryFilter(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                ViolationReviewStatus.CONFIRMED
                        ),
                        List.of(
                                departmentA,
                                departmentB
                        )
                )
        )
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactly(
                        violationB
                );

        assertThat(
                find(
                        new ViolationQueryFilter(
                                Instant.parse(
                                        "2026-08-11T10:30:00Z"
                                ),
                                Instant.parse(
                                        "2026-08-11T11:30:00Z"
                                ),
                                ViolationType.MISSING_GLOVES,
                                cameraA,
                                departmentA,
                                ViolationLifecycleStatus.ACTIVE,
                                ViolationReviewStatus.CONFIRMED
                        ),
                        List.of(
                                departmentA
                        )
                )
        )
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactly(
                        violationB
                );
    }

    @Test
    void alwaysRestrictsResultsToAccessibleDepartments() {
        List<ViolationJpaEntity> result =
                find(
                        emptyFilter(),
                        List.of(
                                departmentA
                        )
                );

        assertThat(result)
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactlyInAnyOrder(
                        violationA,
                        violationB
                )
                .doesNotContain(
                        violationC
                );
    }

    @Test
    void returnsNoRowsWhenUserHasNoAccessibleDepartments() {
        List<ViolationJpaEntity> result =
                find(
                        emptyFilter(),
                        List.of()
                );

        assertThat(result)
                .isEmpty();
    }

    @Test
    void paginationAndStartedAtIdSortAreStable() {
        UUID firstId =
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000001"
                );

        UUID secondId =
                UUID.fromString(
                        "00000000-0000-0000-0000-000000000002"
                );

        Instant sameStartedAt =
                Instant.parse(
                        "2026-08-11T13:00:00Z"
                );

        insertViolation(
                firstId,
                cameraA,
                departmentA,
                ViolationType.MISSING_GLOVES,
                sameStartedAt,
                ViolationLifecycleStatus.COMPLETED,
                ViolationReviewStatus.UNREVIEWED
        );

        insertViolation(
                secondId,
                cameraA,
                departmentA,
                ViolationType.MISSING_GLOVES,
                sameStartedAt,
                ViolationLifecycleStatus.COMPLETED,
                ViolationReviewStatus.UNREVIEWED
        );

        Sort sort =
                Sort.by(
                                Sort.Direction.DESC,
                                "startedAt"
                        )
                        .and(
                                Sort.by(
                                        Sort.Direction.DESC,
                                        "id"
                                )
                        );

        PageRequest firstPage =
                PageRequest.of(
                        0,
                        1,
                        sort
                );

        PageRequest secondPage =
                PageRequest.of(
                        1,
                        1,
                        sort
                );

        ViolationQueryFilter filter =
                new ViolationQueryFilter(
                        sameStartedAt,
                        sameStartedAt,
                        ViolationType.MISSING_GLOVES,
                        cameraA,
                        departmentA,
                        ViolationLifecycleStatus.COMPLETED,
                        ViolationReviewStatus.UNREVIEWED
                );

        Page<ViolationJpaEntity> pageOne =
                violationRepository.findAll(
                        ViolationSpecifications.fromFilter(
                                filter,
                                List.of(
                                        departmentA
                                )
                        ),
                        firstPage
                );

        Page<ViolationJpaEntity> pageTwo =
                violationRepository.findAll(
                        ViolationSpecifications.fromFilter(
                                filter,
                                List.of(
                                        departmentA
                                )
                        ),
                        secondPage
                );

        assertThat(pageOne.getContent())
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactly(
                        secondId
                );

        assertThat(pageTwo.getContent())
                .extracting(
                        ViolationJpaEntity::getId
                )
                .containsExactly(
                        firstId
                );
    }

    private List<ViolationJpaEntity> find(
            ViolationQueryFilter filter,
            List<UUID> accessibleDepartmentIds
    ) {
        return violationRepository.findAll(
                ViolationSpecifications.fromFilter(
                        filter,
                        accessibleDepartmentIds
                )
        );
    }

    private ViolationQueryFilter emptyFilter() {
        return new ViolationQueryFilter(
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    private void insertDepartment(
            UUID id,
            String code,
            String name
    ) {
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
                id,
                code,
                name,
                "Integration test department"
        );
    }

    private void insertCamera(
            UUID id,
            UUID departmentId,
            String code,
            String name
    ) {
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
                id,
                name,
                code,
                departmentId
        );
    }

    private void insertViolation(
            UUID id,
            UUID cameraId,
            UUID departmentId,
            ViolationType type,
            Instant startedAt,
            ViolationLifecycleStatus lifecycleStatus,
            ViolationReviewStatus reviewStatus
    ) {
        Instant reviewedAt =
                reviewStatus == ViolationReviewStatus.UNREVIEWED
                        ? null
                        : startedAt.plusSeconds(60);

        Instant endedAt =
                lifecycleStatus == ViolationLifecycleStatus.COMPLETED
                        || lifecycleStatus == ViolationLifecycleStatus.ERROR
                        ? startedAt.plusSeconds(30)
                        : null;

        OffsetDateTime startedAtDb =
                OffsetDateTime.ofInstant(
                        startedAt,
                        ZoneOffset.UTC
                );

        OffsetDateTime endedAtDb =
                endedAt == null
                        ? null
                        : OffsetDateTime.ofInstant(
                        endedAt,
                        ZoneOffset.UTC
                );

        OffsetDateTime reviewedAtDb =
                reviewedAt == null
                        ? null
                        : OffsetDateTime.ofInstant(
                        reviewedAt,
                        ZoneOffset.UTC
                );

        jdbcTemplate.update(
                """
                        INSERT INTO violations (
                            id,
                            camera_id,
                            department_id,
                            violation_type,
                            started_at,
                            ended_at,
                            confidence,
                            model_version,
                            lifecycle_status,
                            review_status,
                            detected_at,
                            reviewed_at,
                            version
                        )
                        VALUES (?, ?, ?, ?, ?, ?, 0.9000, ?, ?, ?, ?, ?, 0)
                        """,
                id,
                cameraId,
                departmentId,
                type.name(),
                startedAtDb,
                endedAtDb,
                "integration-test-model",
                lifecycleStatus.name(),
                reviewStatus.name(),
                startedAtDb,
                reviewedAtDb
        );
    }
}