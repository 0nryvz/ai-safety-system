package com.isg.backend.reporting.controller;

import com.isg.backend.modules.user.entity.User;
import com.isg.backend.reporting.dto.DashboardDistributionResponse;
import com.isg.backend.reporting.dto.DashboardSummaryResponse;
import com.isg.backend.reporting.dto.DashboardTrendResponse;
import com.isg.backend.reporting.dto.RecentViolationResponse;
import com.isg.backend.reporting.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummaryResponse> getSummary() {
        User user = currentUser();

        return ResponseEntity.ok(
                dashboardService.getSummary(user.getId())
        );
    }

    @GetMapping("/trend")
    public ResponseEntity<List<DashboardTrendResponse>> getTrend(
            @RequestParam LocalDate from,
            @RequestParam LocalDate to,
            @RequestParam(defaultValue = "DAY") String bucket
    ) {
        User user = currentUser();

        return ResponseEntity.ok(
                dashboardService.getTrend(
                        user.getId(),
                        from,
                        to,
                        bucket
                )
        );
    }

    @GetMapping("/distribution")
    public ResponseEntity<List<DashboardDistributionResponse>> getDistribution(
            @RequestParam String groupBy
    ) {
        User user = currentUser();

        return ResponseEntity.ok(
                dashboardService.getDistribution(
                        user.getId(),
                        groupBy
                )
        );
    }

    @GetMapping("/recent-violations")
    public ResponseEntity<List<RecentViolationResponse>> getRecentViolations() {
        User user = currentUser();

        return ResponseEntity.ok(
                dashboardService.getRecentViolations(user.getId())
        );
    }

    private User currentUser() {
        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        return (User) authentication.getPrincipal();
    }
}
