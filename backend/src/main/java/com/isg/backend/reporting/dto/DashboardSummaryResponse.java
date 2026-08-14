package com.isg.backend.reporting.dto;

public record DashboardSummaryResponse(
        long todayViolationCount,
        long last7DaysViolationCount,
        String mostFrequentViolationType,
        long activeCameraCount,
        long offlineCameraCount,
        long activeViolationCount
) {
}