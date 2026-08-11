package com.isg.backend.violation.service;

import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationStatusKind;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationStatusHistoryRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.infrastructure.persistence.ViolationStatusHistoryJpaEntity;
import com.isg.backend.violation.query.ViolationReviewCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationReviewServiceTest {

    private SpringDataViolationRepository violationRepository;
    private SpringDataViolationStatusHistoryRepository statusHistoryRepository;
    private ViolationReviewService reviewService;

    @BeforeEach
    void setUp() {
        violationRepository =
                mock(SpringDataViolationRepository.class);

        statusHistoryRepository =
                mock(
                        SpringDataViolationStatusHistoryRepository.class
                );

        reviewService =
                new ViolationReviewService(
                        violationRepository,
                        statusHistoryRepository
                );
    }

    @Test
    void reviewsViolationAndCreatesAuditHistory() {
        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getId())
                .thenReturn(
                        violationId
                );

        when(violation.getReviewStatus())
                .thenReturn(
                        ViolationReviewStatus.UNREVIEWED
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(violationRepository.save(
                violation
        )).thenReturn(
                violation
        );

        reviewService.review(
                new ViolationReviewCommand(
                        violationId,
                        ViolationReviewStatus.CONFIRMED,
                        reviewerId
                )
        );

        ArgumentCaptor<Instant> reviewedAtCaptor =
                ArgumentCaptor.forClass(
                        Instant.class
                );

        verify(violation)
                .review(
                        org.mockito.ArgumentMatchers.eq(
                                ViolationReviewStatus.CONFIRMED
                        ),
                        org.mockito.ArgumentMatchers.eq(
                                reviewerId
                        ),
                        reviewedAtCaptor.capture()
                );

        verify(violationRepository)
                .save(
                        violation
                );

        ArgumentCaptor<ViolationStatusHistoryJpaEntity> historyCaptor =
                ArgumentCaptor.forClass(
                        ViolationStatusHistoryJpaEntity.class
                );

        verify(statusHistoryRepository)
                .save(
                        historyCaptor.capture()
                );

        ViolationStatusHistoryJpaEntity history =
                historyCaptor.getValue();

        assertThat(history.getViolationId())
                .isEqualTo(
                        violationId
                );

        assertThat(history.getStatusKind())
                .isEqualTo(
                        ViolationStatusKind.REVIEW
                );

        assertThat(history.getFromStatus())
                .isEqualTo(
                        "UNREVIEWED"
                );

        assertThat(history.getToStatus())
                .isEqualTo(
                        "CONFIRMED"
                );

        assertThat(history.getChangedBy())
                .isEqualTo(
                        reviewerId
                );

        assertThat(history.getChangedAt())
                .isEqualTo(
                        reviewedAtCaptor.getValue()
                );
    }

    @Test
    void duplicateReviewStatusDoesNotCreateAnotherAuditEntry() {
        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getReviewStatus())
                .thenReturn(
                        ViolationReviewStatus.FALSE_ALARM
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        reviewService.review(
                new ViolationReviewCommand(
                        violationId,
                        ViolationReviewStatus.FALSE_ALARM,
                        reviewerId
                )
        );

        verify(
                violation,
                never()
        ).review(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );

        verify(
                violationRepository,
                never()
        ).save(
                violation
        );

        verify(
                statusHistoryRepository,
                never()
        ).save(
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void reviewDoesNotChangeLifecycleStatus() {
        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getReviewStatus())
                .thenReturn(
                        ViolationReviewStatus.UNREVIEWED
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.COMPLETED
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(violationRepository.save(
                violation
        )).thenReturn(
                violation
        );

        reviewService.review(
                new ViolationReviewCommand(
                        violationId,
                        ViolationReviewStatus.FALSE_ALARM,
                        reviewerId
                )
        );

        verify(
                violation,
                never()
        ).changeLifecycleStatus(
                org.mockito.ArgumentMatchers.any()
        );

        assertThat(
                violation.getLifecycleStatus()
        )
                .isEqualTo(
                        ViolationLifecycleStatus.COMPLETED
                );
    }

    @Test
    void commandRejectsUnreviewedAsReviewResult() {
        assertThatThrownBy(
                () -> new ViolationReviewCommand(
                        UUID.randomUUID(),
                        ViolationReviewStatus.UNREVIEWED,
                        UUID.randomUUID()
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "REVIEWED"
                );
    }
}