package com.isg.backend.violation.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationStatusKind;
import com.isg.backend.violation.exception.ViolationNotFoundException;
import com.isg.backend.violation.exception.ViolationVersionConflictException;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationStatusHistoryRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.infrastructure.persistence.ViolationStatusHistoryJpaEntity;
import com.isg.backend.violation.query.ViolationReviewCommand;
import com.isg.backend.violation.query.ViolationReviewResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ViolationReviewService {

    private final SpringDataViolationRepository violationRepository;
    private final SpringDataViolationStatusHistoryRepository statusHistoryRepository;
    private final AuthorizationService authorizationService;

    public ViolationReviewService(
            SpringDataViolationRepository violationRepository,
            SpringDataViolationStatusHistoryRepository statusHistoryRepository,
            AuthorizationService authorizationService
    ) {
        this.violationRepository =
                violationRepository;

        this.statusHistoryRepository =
                statusHistoryRepository;

        this.authorizationService =
                authorizationService;
    }

    @Transactional
    public ViolationReviewResponse review(
            ViolationReviewCommand command
    ) {
        Objects.requireNonNull(
                command,
                "command must not be null"
        );

        ViolationJpaEntity violation =
                violationRepository.findById(
                        command.violationId()
                ).orElseThrow(
                        () -> new ViolationNotFoundException(
                                command.violationId()
                        )
                );

        boolean authorized =
                authorizationService.canAccessDepartment(
                        command.reviewerId(),
                        violation.getDepartmentId()
                );

        if (!authorized) {
            throw new ViolationNotFoundException(
                    command.violationId()
            );
        }

        if (violation.getVersion() != command.version()) {
            throw new ViolationVersionConflictException(
                    "Violation version conflict"
            );
        }

        ViolationReviewStatus currentStatus =
                violation.getReviewStatus();

        if (currentStatus == command.reviewStatus()) {
            return toResponse(
                    violation
            );
        }

        Instant reviewedAt =
                Instant.now();

        violation.review(
                command.reviewStatus(),
                command.reviewerId(),
                reviewedAt
        );

        ViolationJpaEntity savedViolation =
                violationRepository.save(
                        violation
                );

        statusHistoryRepository.save(
                new ViolationStatusHistoryJpaEntity(
                        UUID.randomUUID(),
                        violation.getId(),
                        ViolationStatusKind.REVIEW,
                        currentStatus == null
                                ? null
                                : currentStatus.name(),
                        command.reviewStatus().name(),
                        command.reviewerId(),
                        reviewedAt,
                        "Violation reviewed"
                )
        );

        return toResponse(
                savedViolation
        );
    }

    private ViolationReviewResponse toResponse(
            ViolationJpaEntity violation
    ) {
        return new ViolationReviewResponse(
                violation.getId(),
                violation.getReviewStatus(),
                violation.getReviewedBy(),
                violation.getReviewedAt(),
                violation.getVersion()
        );
    }
}