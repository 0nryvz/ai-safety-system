package com.isg.backend.violation.service;

import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationStatusKind;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationStatusHistoryRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.infrastructure.persistence.ViolationStatusHistoryJpaEntity;
import com.isg.backend.violation.query.ViolationReviewCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class ViolationReviewService {

    private final SpringDataViolationRepository violationRepository;
    private final SpringDataViolationStatusHistoryRepository statusHistoryRepository;

    public ViolationReviewService(
            SpringDataViolationRepository violationRepository,
            SpringDataViolationStatusHistoryRepository statusHistoryRepository
    ) {
        this.violationRepository =
                violationRepository;

        this.statusHistoryRepository =
                statusHistoryRepository;
    }

    @Transactional
    public ViolationJpaEntity review(
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
                        () -> new IllegalStateException(
                                "Violation not found: "
                                        + command.violationId()
                        )
                );

        ViolationReviewStatus currentStatus =
                violation.getReviewStatus();

        /*
         * Aynı review sonucu tekrar gönderildiyse
         * gereksiz audit kaydı üretmeyiz.
         */
        if (currentStatus == command.reviewStatus()) {
            return violation;
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

        return savedViolation;
    }
}