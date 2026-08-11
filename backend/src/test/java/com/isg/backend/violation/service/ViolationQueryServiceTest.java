package com.isg.backend.violation.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.query.ViolationListItem;
import com.isg.backend.violation.query.ViolationQueryFilter;
import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.violation.application.port.DepartmentNameResolver;
import com.isg.backend.violation.application.port.RecordingQueryPort;
import com.isg.backend.violation.exception.ViolationNotFoundException;
import com.isg.backend.violation.query.ViolationDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.beans.factory.ObjectProvider;


import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationQueryServiceTest {

    private SpringDataViolationRepository violationRepository;
    private AuthorizationService authorizationService;
    private ViolationQueryService queryService;
    private CameraService cameraService;
    private ObjectProvider<DepartmentNameResolver> departmentNameResolverProvider;
    private ObjectProvider<RecordingQueryPort> recordingQueryPortProvider;

    @BeforeEach
    void setUp() {
        violationRepository =
                mock(SpringDataViolationRepository.class);

        authorizationService =
                mock(AuthorizationService.class);

        cameraService =
                mock(CameraService.class);

        departmentNameResolverProvider =
                mock(ObjectProvider.class);

        recordingQueryPortProvider =
                mock(ObjectProvider.class);

        queryService =
                new ViolationQueryService(
                        violationRepository,
                        authorizationService,
                        cameraService,
                        departmentNameResolverProvider,
                        recordingQueryPortProvider
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
    void returnsAuthorizedViolationDetailWithoutPlaybackUrlWhenRecordingAdapterIsMissing() {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

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

        when(violation.getConfidence())
                .thenReturn(
                        new BigDecimal(
                                "0.9500"
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

        when(violation.getViolationType())
                .thenReturn(
                        ViolationType.MISSING_WELDING_MASK
                );

        when(violation.getModelVersion())
                .thenReturn(
                        "model-v1"
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(authorizationService.canAccessDepartment(
                userId,
                departmentId
        )).thenReturn(
                true
        );

        when(cameraService.getCameraById(
                cameraId
        )).thenReturn(
                CameraResponse.builder()
                        .id(cameraId)
                        .name("Kaynak Kamera")
                        .code("CAM-01")
                        .departmentId(departmentId)
                        .build()
        );

        when(departmentNameResolverProvider.getIfAvailable())
                .thenReturn(
                        null
                );

        when(recordingQueryPortProvider.getIfAvailable())
                .thenReturn(
                        null
                );

        ViolationDetailResponse result =
                queryService.findDetail(
                        userId,
                        violationId
                );

        assertThat(result.violationId())
                .isEqualTo(
                        violationId
                );

        assertThat(result.cameraName())
                .isEqualTo(
                        "Kaynak Kamera"
                );

        assertThat(result.recordingStatus())
                .isEqualTo(
                        "READY"
                );

        assertThat(result.clipReady())
                .isTrue();

        assertThat(result.playbackUrl())
                .isNull();
    }

    @Test
    void hidesUnauthorizedViolationAsNotFound() {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
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
                userId,
                departmentId
        )).thenReturn(
                false
        );

        assertThatThrownBy(
                () -> queryService.findDetail(
                        userId,
                        violationId
                )
        )
                .isInstanceOf(
                        ViolationNotFoundException.class
                );

        verify(
                cameraService,
                never()
        ).getCameraById(
                any()
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