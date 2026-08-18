package com.isg.backend.reporting.infrastructure.persistence;

import com.isg.backend.reporting.dto.RecentViolationResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardRepositoryTest {

    private EntityManager entityManager;
    private Query query;
    private DashboardRepository repository;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        query = mock(Query.class);

        repository = new DashboardRepository();

        ReflectionTestUtils.setField(
                repository,
                "entityManager",
                entityManager
        );

        when(
                entityManager.createNativeQuery(
                        anyString()
                )
        ).thenReturn(
                query
        );

        when(
                query.setParameter(
                        "departmentIds",
                        List.of(
                                UUID.fromString(
                                        "44444444-4444-4444-4444-444444444444"
                                )
                        )
                )
        ).thenReturn(
                query
        );

        when(
                query.setParameter(
                        "limit",
                        10
                )
        ).thenReturn(
                query
        );
    }

    @Test
    void mapsDatabasePendingRecordingStatusToRequestedContractStatus() {
        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.fromString(
                        "44444444-4444-4444-4444-444444444444"
                );

        Object[] row = {
                violationId,
                OffsetDateTime.of(
                        2026,
                        8,
                        18,
                        20,
                        0,
                        0,
                        0,
                        ZoneOffset.UTC
                ),
                OffsetDateTime.of(
                        2026,
                        8,
                        18,
                        20,
                        0,
                        0,
                        0,
                        ZoneOffset.UTC
                ),
                "MISSING_WELDING_MASK",
                cameraId,
                departmentId,
                "Test Camera",
                "TEST-CAM",
                "ACTIVE",
                "UNREVIEWED",
                "PENDING",
                null,
                null,
                null,
                0.95,
                "test-model-v1"
        };

        when(
                query.getResultList()
        ).thenReturn(
                java.util.Collections.singletonList(
                        row
                )
        );

        List<RecentViolationResponse> result =
                repository.getRecentViolations(
                        List.of(
                                departmentId
                        ),
                        10
                );

        assertThat(result)
                .hasSize(1);

        assertThat(
                result.getFirst()
                        .recordingStatus()
        ).isEqualTo(
                "REQUESTED"
        );
    }
}