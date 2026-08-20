package com.isg.backend.violation.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationReviewServiceTest {

    private SpringDataViolationRepository violationRepository;
    private SpringDataViolationStatusHistoryRepository statusHistoryRepository;
    private AuthorizationService authorizationService;
    private ViolationReviewService reviewService;

    @BeforeEach
    void setUp() {
        violationRepository =
                mock(SpringDataViolationRepository.class);

        statusHistoryRepository =
                mock(
                        SpringDataViolationStatusHistoryRepository.class
                );

        authorizationService =
                mock(AuthorizationService.class);

        reviewService =
                new ViolationReviewService(
                        violationRepository,
                        statusHistoryRepository,
                        authorizationService
                );
    }

    @Test
    void reviewsAuthorizedViolationAndCreatesAuditHistory() {
        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getVersion())
                .thenReturn(0L);

        when(violation.getId())
                .thenReturn(
                        violationId
                );

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violation.getReviewStatus())
                .thenReturn(
                        ViolationReviewStatus.UNREVIEWED
                );

        when(violation.getReviewedBy())
                .thenReturn(
                        reviewerId
                );

        when(violation.getReviewedAt())
                .thenAnswer(
                        invocation -> Instant.now()
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(authorizationService.canAccessDepartment(
                reviewerId,
                departmentId
        )).thenReturn(
                true
        );

        when(violationRepository.save(
                violation
        )).thenReturn(
                violation
        );

        ViolationReviewResponse response =
                reviewService.review(
                        new ViolationReviewCommand(
                                violationId,
                                ViolationReviewStatus.REVIEWED,
                                reviewerId,
                                0L
                        )
                );

        ArgumentCaptor<Instant> reviewedAtCaptor =
                ArgumentCaptor.forClass(
                        Instant.class
                );

        verify(violation)
                .review(
                        eq(
                                ViolationReviewStatus.REVIEWED
                        ),
                        eq(
                                reviewerId
                        ),
                        reviewedAtCaptor.capture()
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
                        "REVIEWED"
                );
        assertThat(history.getChangedBy())
                .isEqualTo(
                        reviewerId
                );

        assertThat(response.violationId())
                .isEqualTo(
                        violationId
                );
    }

    @Test
    void hidesUnauthorizedReviewAsNotFound() {
        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getVersion())
                .thenReturn(0L);

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(authorizationService.canAccessDepartment(
                reviewerId,
                departmentId
        )).thenReturn(
                false
        );

        assertThatThrownBy(
                () -> reviewService.review(
                        new ViolationReviewCommand(
                                violationId,
                                ViolationReviewStatus.REVIEWED,
                                reviewerId,
                                0L
                        )
                )
        )
                .isInstanceOf(
                        ViolationNotFoundException.class
                );

        verify(
                violationRepository,
                never()
        ).save(
                any()
        );

        verify(
                statusHistoryRepository,
                never()
        ).save(
                any()
        );
    }

    @Test
    void duplicateReviewStatusDoesNotCreateAnotherAuditEntry() {
        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getVersion())
                .thenReturn(0L);

        when(violation.getId())
                .thenReturn(
                        violationId
                );

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violation.getReviewStatus())
                .thenReturn(
                        ViolationReviewStatus.REVIEWED
                );

        when(violation.getReviewedBy())
                .thenReturn(
                        reviewerId
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(authorizationService.canAccessDepartment(
                reviewerId,
                departmentId
        )).thenReturn(
                true
        );

        reviewService.review(
                new ViolationReviewCommand(
                        violationId,
                        ViolationReviewStatus.REVIEWED,
                        reviewerId,
                        0L
                )
        );

        verify(
                violation,
                never()
        ).review(
                any(),
                any(),
                any()
        );

        verify(
                violationRepository,
                never()
        ).save(
                any()
        );

        verify(
                statusHistoryRepository,
                never()
        ).save(
                any()
        );
    }

    @Test
    void reviewDoesNotChangeLifecycleStatus() {
        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getVersion())
                .thenReturn(0L);

        when(violation.getId())
                .thenReturn(
                        violationId
                );

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violation.getReviewStatus())
                .thenReturn(
                        ViolationReviewStatus.UNREVIEWED,
                        ViolationReviewStatus.FALSE_ALARM
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.COMPLETED
                );

        when(authorizationService.canAccessDepartment(
                reviewerId,
                departmentId
        )).thenReturn(
                true
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
                        ViolationReviewStatus.REVIEWED,
                        reviewerId,
                        0L
                )
        );

        verify(
                violation,
                never()
        ).changeLifecycleStatus(
                any()
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

        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        assertThatThrownBy(
                () -> new ViolationReviewCommand(
                        violationId,
                        ViolationReviewStatus.UNREVIEWED,
                        reviewerId,
                        0L
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "REVIEWED"
                );
    }

    @Test
    void versionConflictThrowsExceptionAndDoesNotPersistReview() {
        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getVersion())
                .thenReturn(5L);

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(authorizationService.canAccessDepartment(
                reviewerId,
                departmentId
        )).thenReturn(
                true
        );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        assertThatThrownBy(
                () -> reviewService.review(
                        new ViolationReviewCommand(
                                violationId,
                                ViolationReviewStatus.CONFIRMED,
                                reviewerId,
                                4L
                        )
                )
        )
                .isInstanceOf(
                        ViolationVersionConflictException.class
                );

        verify(
                violation,
                never()
        ).review(
                any(),
                any(),
                any()
        );

        verify(
                violationRepository,
                never()
        ).save(
                any()
        );

        verify(
                statusHistoryRepository,
                never()
        ).save(
                any()
        );
    }

    @ParameterizedTest
    @EnumSource(
            value = ViolationReviewStatus.class,
            names = {
                    "REVIEWED",
                    "CONFIRMED",
                    "FALSE_ALARM"
            }
    )
    void supportsAllReviewResultStatuses(
            ViolationReviewStatus reviewStatus
    ) {
        UUID violationId =
                UUID.randomUUID();

        UUID reviewerId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getVersion())
                .thenReturn(0L);

        when(violation.getId())
                .thenReturn(
                        violationId
                );

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violation.getReviewStatus())
                .thenReturn(
                        ViolationReviewStatus.UNREVIEWED,
                        reviewStatus
                );

        when(violation.getReviewedBy())
                .thenReturn(
                        reviewerId
                );

        when(violation.getReviewedAt())
                .thenReturn(
                        Instant.now()
                );

        when(
                violationRepository.findById(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(
                authorizationService.canAccessDepartment(
                        reviewerId,
                        departmentId
                )
        ).thenReturn(
                true
        );

        when(
                violationRepository.save(
                        violation
                )
        ).thenReturn(
                violation
        );

        ViolationReviewResponse response =
                reviewService.review(
                        new ViolationReviewCommand(
                                violationId,
                                reviewStatus,
                                reviewerId,
                                0L
                        )
                );

        assertThat(
                response.reviewStatus()
        ).isEqualTo(
                reviewStatus
        );

        verify(
                violation
        ).review(
                eq(reviewStatus),
                eq(reviewerId),
                any(Instant.class)
        );

        verify(
                statusHistoryRepository
        ).save(
                any(
                        ViolationStatusHistoryJpaEntity.class
                )
        );
    }
}

