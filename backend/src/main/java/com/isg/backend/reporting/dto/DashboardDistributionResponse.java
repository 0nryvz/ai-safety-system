package com.isg.backend.reporting.dto;

public record DashboardDistributionResponse(
        String group,
        long count
) {
}