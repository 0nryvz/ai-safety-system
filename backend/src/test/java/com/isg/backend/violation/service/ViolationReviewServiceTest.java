package com.isg.backend.violation.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationStatusKind;
import com.isg.backend.violation.exception.ViolationNotFoundException;
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
                        ViolationReviewStatus.CONFIRMED
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
                        eq(
                                ViolationReviewStatus.CONFIRMED
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
                        "CONFIRMED"
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
                                ViolationReviewStatus.CONFIRMED,
                                reviewerId
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
                        ViolationReviewStatus.FALSE_ALARM
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
                        ViolationReviewStatus.FALSE_ALARM,
                        reviewerId
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
                        ViolationReviewStatus.FALSE_ALARM,
                        reviewerId
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
                                reviewerId
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

