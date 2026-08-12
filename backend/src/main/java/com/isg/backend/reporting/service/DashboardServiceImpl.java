package com.isg.backend.reporting.service;

import com.isg.backend.reporting.dto.DashboardDistributionResponse;
import com.isg.backend.reporting.dto.DashboardSummaryResponse;
import com.isg.backend.reporting.dto.DashboardTrendResponse;
import com.isg.backend.reporting.dto.RecentViolationResponse;
import com.isg.backend.reporting.infrastructure.persistence.DashboardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardRepository dashboardRepository;

    @Override
    public DashboardSummaryResponse getSummary() {
        return dashboardRepository.getSummary();
    }

    @Override
    public List<DashboardTrendResponse> getTrend(
            LocalDate from,
            LocalDate to
    ) {
        return dashboardRepository.getTrend(from, to);
    }

    @Override
    public List<DashboardDistributionResponse> getDistribution(
            String groupBy
    ) {
        return dashboardRepository.getDistribution(groupBy);
    }

    @Override
    public List<RecentViolationResponse> getRecentViolations() {
        return dashboardRepository.getRecentViolations();
    }
}