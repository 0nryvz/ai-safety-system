package com.isg.backend.violation.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.query.ViolationListItem;
import com.isg.backend.violation.query.ViolationQueryFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationQueryServiceTest {

    private SpringDataViolationRepository violationRepository;
    private AuthorizationService authorizationService;
    private ViolationQueryService queryService;

    @BeforeEach
    void setUp() {
        violationRepository =
                mock(SpringDataViolationRepository.class);

        authorizationService =
                mock(AuthorizationService.class);

        queryService =
                new ViolationQueryService(
                        violationRepository,
                        authorizationService
                );
    }

    @Test
    void appliesAccessibleDepartmentsAndMapsLightweightResult() {
        UUID userId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-11T10:00:00Z"
                );

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getId())
                .thenReturn(
                        violationId
                );

        when(violation.getCameraId())
                .thenReturn(
                        cameraId
                );

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violation.getViolationType())
                .thenReturn(
                        ViolationType.MISSING_WELDING_MASK
                );

        when(violation.getStartedAt())
                .thenReturn(
                        startedAt
                );

        when(violation.getConfidence())
                .thenReturn(
                        new BigDecimal(
                                "0.9100"
                        )
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.COMPLETED
                );

        when(violation.getReviewStatus())
                .thenReturn(
                        ViolationReviewStatus.UNREVIEWED
                );

        when(authorizationService.accessibleDepartmentIds(
                userId
        )).thenReturn(
                List.of(
                        departmentId
                )
        );

        PageRequest pageable =
                PageRequest.of(
                        0,
                        20
                );

        when(violationRepository.findAll(
                any(Specification.class),
                org.mockito.ArgumentMatchers.eq(
                        pageable
                )
        )).thenReturn(
                new PageImpl<>(
                        List.of(
                                violation
                        ),
                        pageable,
                        1
                )
        );

        Page<ViolationListItem> result =
                queryService.findViolations(
                        userId,
                        new ViolationQueryFilter(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        pageable
                );

        verify(authorizationService)
                .accessibleDepartmentIds(
                        userId
                );

        assertThat(result.getTotalElements())
                .isEqualTo(
                        1
                );

        ViolationListItem item =
                result.getContent()
                        .getFirst();

        assertThat(item.violationId())
                .isEqualTo(
                        violationId
                );

        assertThat(item.departmentId())
                .isEqualTo(
                        departmentId
                );

        assertThat(item.cameraId())
                .isEqualTo(
                        cameraId
                );

        assertThat(item.lifecycleStatus())
                .isEqualTo(
                        ViolationLifecycleStatus.COMPLETED
                );
    }

    @Test
    void emptyAccessibleDepartmentsStillUsesSecuredSpecification() {
        UUID userId =
                UUID.randomUUID();

        PageRequest pageable =
                PageRequest.of(
                        0,
                        20
                );

        when(authorizationService.accessibleDepartmentIds(
                userId
        )).thenReturn(
                List.of()
        );

        when(violationRepository.findAll(
                any(Specification.class),
                org.mockito.ArgumentMatchers.eq(
                        pageable
                )
        )).thenReturn(
                Page.empty(
                        pageable
                )
        );

        Page<ViolationListItem> result =
                queryService.findViolations(
                        userId,
                        new ViolationQueryFilter(
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null
                        ),
                        pageable
                );

        assertThat(result)
                .isEmpty();

        ArgumentCaptor<Specification<ViolationJpaEntity>> specificationCaptor =
                ArgumentCaptor.forClass(
                        Specification.class
                );

        verify(violationRepository)
                .findAll(
                        specificationCaptor.capture(),
                        org.mockito.ArgumentMatchers.eq(
                                pageable
                        )
                );

        assertThat(specificationCaptor.getValue())
                .isNotNull();
    }
}