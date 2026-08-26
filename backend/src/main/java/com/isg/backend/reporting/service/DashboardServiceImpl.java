package com.isg.backend.reporting.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.reporting.dto.DashboardDistributionResponse;
import com.isg.backend.reporting.dto.DashboardSummaryResponse;
import com.isg.backend.reporting.dto.DashboardTrendResponse;
import com.isg.backend.reporting.dto.RecentViolationResponse;
import com.isg.backend.reporting.infrastructure.persistence.DashboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardRepository dashboardRepository;
    private final AuthorizationService authorizationService;

    @Override
    public DashboardSummaryResponse getSummary(UUID userId) {
        List<UUID> departmentIds =
                authorizationService.accessibleDepartmentIds(userId);

        if (departmentIds.isEmpty()) {
            return new DashboardSummaryResponse(
                    0,
                    0,
                    null,
                    0,
                    0,
                    0
            );
        }

        return dashboardRepository.getSummary(departmentIds);
    }

    @Override
    public List<DashboardTrendResponse> getTrend(
            UUID userId,
            LocalDate from,
            LocalDate to,
            String bucket
    ) {
        List<UUID> departmentIds =
                authorizationService.accessibleDepartmentIds(userId);

        if (departmentIds.isEmpty()) {
            return List.of();
        }

        return dashboardRepository.getTrend(
                from,
                to,
                bucket,
                departmentIds
        );
    }

    @Override
    public List<DashboardDistributionResponse> getDistribution(
            UUID userId,
            String groupBy
    ) {
        List<UUID> departmentIds =
                authorizationService.accessibleDepartmentIds(userId);

        if (departmentIds.isEmpty()) {
            return List.of();
        }

        return dashboardRepository.getDistribution(
                groupBy,
                departmentIds
        );
    }

    @Override
    public List<RecentViolationResponse> getRecentViolations(UUID userId) {
        List<UUID> departmentIds =
                authorizationService.accessibleDepartmentIds(userId);

        if (departmentIds.isEmpty()) {
            return List.of();
        }

        return dashboardRepository.getRecentViolations(
                departmentIds,
                20
        );
    }
}
