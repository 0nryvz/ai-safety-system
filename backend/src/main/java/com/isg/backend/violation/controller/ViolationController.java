package com.isg.backend.violation.controller;

import com.isg.backend.modules.user.dto.UserResponse;
import com.isg.backend.modules.user.service.UserService;
import com.isg.backend.shared.web.PageResponse;
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
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.data.domain.Sort.Direction.DESC;

@RestController
@RequestMapping("/api/v1/violations")
public class ViolationController {

    private final ViolationQueryService queryService;
    private final ViolationReviewService reviewService;
    private final UserService userService;

    public ViolationController(
            ViolationQueryService queryService,
            ViolationReviewService reviewService,
            UserService userService
    ) {
        this.queryService =
                queryService;

        this.reviewService =
                reviewService;

        this.userService =
                userService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<ViolationListItem>> findViolations(
            Authentication authentication,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant from,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            Instant to,

            @RequestParam(required = false)
            ViolationType type,

            @RequestParam(required = false)
            UUID cameraId,

            @RequestParam(required = false)
            UUID departmentId,

            @RequestParam(required = false)
            ViolationLifecycleStatus lifecycleStatus,

            @RequestParam(required = false)
            ViolationReviewStatus reviewStatus,

            @RequestParam(required = false)
            String recordingStatus,

            @PageableDefault(
                    size = 20,
                    sort = {
                            "startedAt",
                            "id"
                    },
                    direction = DESC
            )
            Pageable pageable
    ) {
        UUID userId =
                resolveCurrentUserId(
                        authentication
                );

        ViolationQueryFilter filter =
                new ViolationQueryFilter(
                        from,
                        to,
                        type,
                        cameraId,
                        departmentId,
                        lifecycleStatus,
                        reviewStatus,
                        recordingStatus
                );

        Page<ViolationListItem> result =
                queryService.findViolations(
                        userId,
                        filter,
                        pageable
                );

        return ResponseEntity.ok(
                new PageResponse<>(
                        result.getContent(),
                        result.getNumber(),
                        result.getSize(),
                        result.getTotalElements(),
                        result.getTotalPages()
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ViolationDetailResponse> findDetail(
            @PathVariable
            UUID id,
            Authentication authentication
    ) {
        UUID userId =
                resolveCurrentUserId(
                        authentication
                );

        return ResponseEntity.ok(
                queryService.findDetail(
                        userId,
                        id
                )
        );
    }

    @PatchMapping("/{id}/review")
    public ResponseEntity<ViolationReviewResponse> reviewViolation(
            @PathVariable
            UUID id,
            @Valid
            @RequestBody
            ViolationReviewRequest request,
            Authentication authentication
    ) {
        UUID reviewerId =
                resolveCurrentUserId(
                        authentication
                );

        return ResponseEntity.ok(
                reviewService.review(
                        new ViolationReviewCommand(
                                id,
                                request.reviewStatus(),
                                reviewerId,
                                request.version()
                        )
                )
        );
    }

    private UUID resolveCurrentUserId(
            Authentication authentication
    ) {
        Objects.requireNonNull(
                authentication,
                "authentication must not be null"
        );

        UserResponse currentUser =
                userService.getMe(
                        authentication.getName()
                );

        if (currentUser.getId() == null) {
            throw new IllegalStateException(
                    "Authenticated user id must not be null"
            );
        }

        return currentUser.getId();
    }
}