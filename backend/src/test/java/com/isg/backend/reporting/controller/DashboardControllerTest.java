package com.isg.backend.reporting.controller;

import com.isg.backend.modules.user.entity.User;
import com.isg.backend.reporting.service.DashboardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DashboardControllerTest {

    private DashboardService dashboardService;
    private DashboardController controller;
    private User user;

    @BeforeEach
    void setUp() {
        dashboardService = mock(DashboardService.class);
        controller = new DashboardController(dashboardService);

        user = mock(User.class);

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of()
                )
        );
    }

    @Test
    void trendRejectsFromAfterToWith400() {
        LocalDate from = LocalDate.of(2026, 8, 20);
        LocalDate to = LocalDate.of(2026, 8, 19);

        assertThatThrownBy(
                () -> controller.getTrend(
                        from,
                        to,
                        "DAY"
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException =
                            (ResponseStatusException) ex;

                    assertThat(
                            responseStatusException.getStatusCode()
                    ).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void trendRejectsUnsupportedBucketWith400() {
        LocalDate from = LocalDate.of(2026, 8, 19);
        LocalDate to = LocalDate.of(2026, 8, 20);

        assertThatThrownBy(
                () -> controller.getTrend(
                        from,
                        to,
                        "WEEK"
                )
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException =
                            (ResponseStatusException) ex;

                    assertThat(
                            responseStatusException.getStatusCode()
                    ).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void trendAcceptsDayAndDelegatesToService() {
        UUID userId = UUID.randomUUID();

        LocalDate from = LocalDate.of(2026, 8, 19);
        LocalDate to = LocalDate.of(2026, 8, 20);

        when(user.getId()).thenReturn(userId);

        controller.getTrend(
                from,
                to,
                "DAY"
        );

        verify(dashboardService).getTrend(
                userId,
                from,
                to,
                "DAY"
        );
    }

    @Test
    void distributionRejectsUnsupportedGroupByWith400() {
        assertThatThrownBy(
                () -> controller.getDistribution("UNKNOWN")
        )
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException responseStatusException =
                            (ResponseStatusException) ex;

                    assertThat(
                            responseStatusException.getStatusCode()
                    ).isEqualTo(HttpStatus.BAD_REQUEST);
                });
    }

    @Test
    void distributionAcceptsSupportedGroupByValues() {
        UUID userId = UUID.randomUUID();

        when(user.getId()).thenReturn(userId);

        controller.getDistribution("TYPE");
        controller.getDistribution("CAMERA");
        controller.getDistribution("DEPARTMENT");

        verify(dashboardService).getDistribution(
                userId,
                "TYPE"
        );

        verify(dashboardService).getDistribution(
                userId,
                "CAMERA"
        );

        verify(dashboardService).getDistribution(
                userId,
                "DEPARTMENT"
        );
    }
}