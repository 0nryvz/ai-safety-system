package com.isg.backend.reporting.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.reporting.dto.DashboardSummaryResponse;
import com.isg.backend.reporting.infrastructure.persistence.DashboardRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DashboardServiceImplTest {

    private DashboardRepository dashboardRepository;
    private AuthorizationService authorizationService;
    private DashboardServiceImpl dashboardService;

    @BeforeEach
    void setUp() {
        dashboardRepository =
                mock(DashboardRepository.class);

        authorizationService =
                mock(AuthorizationService.class);

        dashboardService =
                new DashboardServiceImpl(
                        dashboardRepository,
                        authorizationService
                );
    }

    @Test
    void passesAuthorizedDepartmentsToSummary() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        List<UUID> departmentIds =
                List.of(departmentId);

        DashboardSummaryResponse expected =
                new DashboardSummaryResponse(
                        3,
                        8,
                        "NO_HELMET",
                        2,
                        1,
                        1
                );

        when(authorizationService.accessibleDepartmentIds(userId))
                .thenReturn(departmentIds);

        when(dashboardRepository.getSummary(departmentIds))
                .thenReturn(expected);

        DashboardSummaryResponse actual =
                dashboardService.getSummary(userId);

        assertThat(actual)
                .isEqualTo(expected);

        verify(dashboardRepository)
                .getSummary(departmentIds);
    }

    @Test
    void passesAuthorizedDepartmentsToTrendAndDistribution() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        List<UUID> departmentIds =
                List.of(departmentId);

        LocalDate from =
                LocalDate.of(2026, 8, 10);

        LocalDate to =
                LocalDate.of(2026, 8, 12);

        when(authorizationService.accessibleDepartmentIds(userId))
                .thenReturn(departmentIds);

        when(dashboardRepository.getTrend(
                from,
                to,
                "DAY",
                departmentIds
        )).thenReturn(List.of());

        when(dashboardRepository.getDistribution(
                "TYPE",
                departmentIds
        )).thenReturn(List.of());

        dashboardService.getTrend(
                userId,
                from,
                to,
                "DAY"
        );

        dashboardService.getDistribution(
                userId,
                "TYPE"
        );

        verify(dashboardRepository)
                .getTrend(
                        from,
                        to,
                        "DAY",
                        departmentIds
                );

        verify(dashboardRepository)
                .getDistribution(
                        "TYPE",
                        departmentIds
                );
    }

    @Test
    void recentViolationsUsesAuthorizedDepartmentsAndDefaultLimit() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        List<UUID> departmentIds =
                List.of(departmentId);

        when(authorizationService.accessibleDepartmentIds(userId))
                .thenReturn(departmentIds);

        when(dashboardRepository.getRecentViolations(
                departmentIds,
                20
        )).thenReturn(List.of());

        dashboardService.getRecentViolations(userId);

        verify(dashboardRepository)
                .getRecentViolations(
                        departmentIds,
                        20
                );
    }

    @Test
    void emptyDepartmentAccessReturnsEmptyDashboardWithoutRepositoryCalls() {
        UUID userId = UUID.randomUUID();

        when(authorizationService.accessibleDepartmentIds(userId))
                .thenReturn(List.of());

        DashboardSummaryResponse summary =
                dashboardService.getSummary(userId);

        assertThat(summary.todayViolationCount())
                .isZero();

        assertThat(summary.last7DaysViolationCount())
                .isZero();

        assertThat(summary.mostFrequentViolationType())
                .isNull();

        assertThat(summary.activeCameraCount())
                .isZero();

        assertThat(summary.offlineCameraCount())
                .isZero();

        assertThat(summary.activeViolationCount())
                .isZero();

        assertThat(
                dashboardService.getTrend(
                        userId,
                        LocalDate.of(2026, 8, 10),
                        LocalDate.of(2026, 8, 12),
                        "DAY"
                )
        ).isEmpty();

        assertThat(
                dashboardService.getDistribution(
                        userId,
                        "TYPE"
                )
        ).isEmpty();

        assertThat(
                dashboardService.getRecentViolations(userId)
        ).isEmpty();

        verifyNoInteractions(dashboardRepository);
    }
}