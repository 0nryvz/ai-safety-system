package com.isg.backend.reporting.service;

import com.isg.backend.reporting.dto.DashboardDistributionResponse;
import com.isg.backend.reporting.dto.DashboardSummaryResponse;
import com.isg.backend.reporting.dto.DashboardTrendResponse;
import com.isg.backend.reporting.dto.RecentViolationResponse;

import java.time.LocalDate;
import java.util.List;

public interface DashboardService {

    DashboardSummaryResponse getSummary();

    List<DashboardTrendResponse> getTrend(
            LocalDate from,
            LocalDate to
    );

    List<DashboardDistributionResponse> getDistribution(
            String groupBy
    );

    List<RecentViolationResponse> getRecentViolations();
}