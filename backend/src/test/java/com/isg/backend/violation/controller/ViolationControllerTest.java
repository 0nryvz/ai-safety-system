package com.isg.backend.violation.controller;

import com.isg.backend.modules.user.dto.UserResponse;
import com.isg.backend.modules.user.service.UserService;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.query.ViolationDetailResponse;
import com.isg.backend.violation.query.ViolationListItem;
import com.isg.backend.violation.query.ViolationQueryFilter;
import com.isg.backend.violation.query.ViolationReviewCommand;
import com.isg.backend.violation.query.ViolationReviewRequest;
import com.isg.backend.violation.query.ViolationReviewResponse;
import com.isg.backend.violation.service.ViolationQueryService;
import com.isg.backend.violation.service.ViolationReviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationControllerTest {

    private ViolationQueryService queryService;
    private ViolationReviewService reviewService;
    private UserService userService;
    private Authentication authentication;
    private ViolationController controller;

    @BeforeEach
    void setUp() {
        queryService =
                mock(ViolationQueryService.class);

        reviewService =
                mock(ViolationReviewService.class);

        userService =
                mock(UserService.class);

        authentication =
                mock(Authentication.class);

        controller =
                new ViolationController(
                        queryService,
                        reviewService,
                        userService
                );
    }

    @Test
    void listResolvesAuthenticatedUserAndPassesFilters() {
        UUID userId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant from =
                Instant.parse(
                        "2026-08-10T00:00:00Z"
                );

        Instant to =
                Instant.parse(
                        "2026-08-11T23:59:59Z"
                );

        PageRequest pageable =
                PageRequest.of(
                        0,
                        20
                );

        when(authentication.getName())
                .thenReturn(
                        "user@example.com"
                );

        when(userService.getMe(
                "user@example.com"
        )).thenReturn(
                UserResponse.builder()
                        .id(userId)
                        .email("user@example.com")
                        .build()
        );

        Page<ViolationListItem> page =
                new PageImpl<>(
                        List.of(),
                        pageable,
                        0
                );

        when(queryService.findViolations(
                org.mockito.ArgumentMatchers.eq(
                        userId
                ),
                org.mockito.ArgumentMatchers.any(
                        ViolationQueryFilter.class
                ),
                org.mockito.ArgumentMatchers.eq(
                        pageable
                )
        )).thenReturn(
                page
        );

        ResponseEntity<Page<ViolationListItem>> response =
                controller.findViolations(
                        authentication,
                        from,
                        to,
                        ViolationType.MISSING_WELDING_MASK,
                        cameraId,
                        departmentId,
                        ViolationLifecycleStatus.COMPLETED,
                        ViolationReviewStatus.CONFIRMED,
                        pageable
                );

        ArgumentCaptor<ViolationQueryFilter> filterCaptor =
                ArgumentCaptor.forClass(
                        ViolationQueryFilter.class
                );

        verify(queryService)
                .findViolations(
                        org.mockito.ArgumentMatchers.eq(
                                userId
                        ),
                        filterCaptor.capture(),
                        org.mockito.ArgumentMatchers.eq(
                                pageable
                        )
                );

        ViolationQueryFilter filter =
                filterCaptor.getValue();

        assertThat(filter.from())
                .isEqualTo(
                        from
                );

        assertThat(filter.to())
                .isEqualTo(
                        to
                );

        assertThat(filter.type())
                .isEqualTo(
                        ViolationType.MISSING_WELDING_MASK
                );

        assertThat(filter.cameraId())
                .isEqualTo(
                        cameraId
                );

        assertThat(filter.departmentId())
                .isEqualTo(
                        departmentId
                );

        assertThat(filter.lifecycleStatus())
                .isEqualTo(
                        ViolationLifecycleStatus.COMPLETED
                );

        assertThat(filter.reviewStatus())
                .isEqualTo(
                        ViolationReviewStatus.CONFIRMED
                );

        assertThat(response.getBody())
                .isSameAs(
                        page
                );
    }

    @Test
    void detailUsesAuthenticatedUserId() {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        when(authentication.getName())
                .thenReturn(
                        "user@example.com"
                );

        when(userService.getMe(
                "user@example.com"
        )).thenReturn(
                UserResponse.builder()
                        .id(userId)
                        .build()
        );

        ViolationDetailResponse detail =
                mock(ViolationDetailResponse.class);

        when(queryService.findDetail(
                userId,
                violationId
        )).thenReturn(
                detail
        );

        ResponseEntity<ViolationDetailResponse> response =
                controller.findDetail(
                        violationId,
                        authentication
                );

        verify(queryService)
                .findDetail(
                        userId,
                        violationId
                );

        assertThat(response.getBody())
                .isSameAs(
                        detail
                );
    }

    @Test
    void reviewUsesAuthenticatedUserAsReviewer() {
        UUID reviewerId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        when(authentication.getName())
                .thenReturn(
                        "reviewer@example.com"
                );

        when(userService.getMe(
                "reviewer@example.com"
        )).thenReturn(
                UserResponse.builder()
                        .id(reviewerId)
                        .build()
        );

        ViolationReviewResponse reviewResponse =
                new ViolationReviewResponse(
                        violationId,
                        ViolationReviewStatus.FALSE_ALARM,
                        reviewerId,
                        Instant.parse(
                                "2026-08-11T20:00:00Z"
                        )
                );

        when(reviewService.review(
                org.mockito.ArgumentMatchers.any(
                        ViolationReviewCommand.class
                )
        )).thenReturn(
                reviewResponse
        );

        ResponseEntity<ViolationReviewResponse> response =
                controller.reviewViolation(
                        violationId,
                        new ViolationReviewRequest(
                                ViolationReviewStatus.FALSE_ALARM
                        ),
                        authentication
                );

        ArgumentCaptor<ViolationReviewCommand> commandCaptor =
                ArgumentCaptor.forClass(
                        ViolationReviewCommand.class
                );

        verify(reviewService)
                .review(
                        commandCaptor.capture()
                );

        ViolationReviewCommand command =
                commandCaptor.getValue();

        assertThat(command.violationId())
                .isEqualTo(
                        violationId
                );

        assertThat(command.reviewStatus())
                .isEqualTo(
                        ViolationReviewStatus.FALSE_ALARM
                );

        assertThat(command.reviewerId())
                .isEqualTo(
                        reviewerId
                );

        assertThat(response.getBody())
                .isSameAs(
                        reviewResponse
                );
    }
}