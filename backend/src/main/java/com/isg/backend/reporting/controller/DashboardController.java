package com.isg.backend.reporting.controller;

import com.isg.backend.reporting.dto.DashboardDistributionResponse;
import com.isg.backend.reporting.dto.DashboardSummaryResponse;
import com.isg.backend.reporting.dto.DashboardTrendResponse;
import com.isg.backend.reporting.dto.RecentViolationResponse;
import com.isg.backend.reporting.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.isg.backend.modules.user.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        return ResponseEntity.ok(
                dashboardService.getSummary()
        );
    }

    @GetMapping("/trend")
    public ResponseEntity<List<DashboardTrendResponse>> getTrend(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to
    ) {
        return ResponseEntity.ok(
                dashboardService.getTrend(from, to)
        );
    }

    @GetMapping("/distribution")
    public ResponseEntity<List<DashboardDistributionResponse>> getDistribution(
            @RequestParam String groupBy
    ) {
        return ResponseEntity.ok(
                dashboardService.getDistribution(groupBy)
        );
    }

    @GetMapping("/recent-violations")
    public ResponseEntity<List<RecentViolationResponse>> getRecentViolations() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        User user = (User) authentication.getPrincipal();

        return ResponseEntity.ok(
                dashboardService.getRecentViolations(user.getId())
        );
    }
}