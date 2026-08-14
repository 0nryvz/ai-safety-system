package com.isg.backend.reporting.infrastructure.persistence.projection;

public interface DashboardSummaryProjection {

    long getTodayViolationCount();

    long getLast7DaysViolationCount();

    String getMostFrequentViolationType();

    long getActiveCameraCount();

    long getOfflineCameraCount();

    long getActiveViolationCount();
}