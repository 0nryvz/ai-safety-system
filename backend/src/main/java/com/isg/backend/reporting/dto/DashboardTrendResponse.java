package com.isg.backend.reporting.dto;

import java.time.LocalDate;

public record DashboardTrendResponse(
        LocalDate date,
        long count
) {
}