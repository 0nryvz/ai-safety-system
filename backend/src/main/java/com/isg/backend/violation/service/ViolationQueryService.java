package com.isg.backend.violation.service;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.application.port.DepartmentNameResolver;
import com.isg.backend.violation.application.port.RecordingQueryPort;
import com.isg.backend.violation.application.port.RecordingQueryResult;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.exception.ViolationNotFoundException;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.infrastructure.persistence.ViolationSpecifications;
import com.isg.backend.violation.query.ViolationDetailResponse;
import com.isg.backend.violation.query.ViolationListItem;
import com.isg.backend.violation.query.ViolationQueryFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ViolationQueryService {

    private final SpringDataViolationRepository violationRepository;
    private final AuthorizationService authorizationService;
    private final CameraService cameraService;
    private final ObjectProvider<DepartmentNameResolver> departmentNameResolverProvider;
    private final ObjectProvider<RecordingQueryPort> recordingQueryPortProvider;

    public ViolationQueryService(
            SpringDataViolationRepository violationRepository,
            AuthorizationService authorizationService,
            CameraService cameraService,
            ObjectProvider<DepartmentNameResolver> departmentNameResolverProvider,
            ObjectProvider<RecordingQueryPort> recordingQueryPortProvider
    ) {
        this.violationRepository =
                violationRepository;

        this.authorizationService =
                authorizationService;

        this.cameraService =
                cameraService;

        this.departmentNameResolverProvider =
                departmentNameResolverProvider;

        this.recordingQueryPortProvider =
                recordingQueryPortProvider;
    }

    public Page<ViolationListItem> findViolations(
            UUID userId,
            ViolationQueryFilter filter,
            Pageable pageable
    ) {
        Objects.requireNonNull(
                userId,
                "userId must not be null"
        );

        Objects.requireNonNull(
                filter,
                "filter must not be null"
        );

        Objects.requireNonNull(
                pageable,
                "pageable must not be null"
        );

        List<UUID> accessibleDepartmentIds =
                authorizationService.accessibleDepartmentIds(
                        userId
                );

        return violationRepository.findAll(
                        ViolationSpecifications.fromFilter(
                                filter,
                                accessibleDepartmentIds
                        ),
                        pageable
                )
                .map(
                        this::toListItem
                );
    }

    public ViolationDetailResponse findDetail(
            UUID userId,
            UUID violationId
    ) {
        Objects.requireNonNull(
                userId,
                "userId must not be null"
        );

        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        ViolationJpaEntity violation =
                violationRepository.findById(
                        violationId
                ).orElseThrow(
                        () -> new ViolationNotFoundException(
                                violationId
                        )
                );

        boolean authorized =
                authorizationService.canAccessDepartment(
                        userId,
                        violation.getDepartmentId()
                );

        if (!authorized) {
            throw new ViolationNotFoundException(
                    violationId
            );
        }

        CameraResponse camera =
                cameraService.getCameraById(
                        violation.getCameraId()
                );

        String departmentName =
                resolveDepartmentName(
                        violation.getDepartmentId()
                );

        RecordingQueryResult recording =
                resolveRecording(
                        violation
                );

        String coverImageKey =
                violation.getCoverImageKey();

        boolean coverImageReady =
                coverImageKey != null
                        && !coverImageKey.isBlank();

        return new ViolationDetailResponse(
                violation.getId(),
                violation.getCameraId(),
                camera.getName(),
                camera.getCode(),
                violation.getDepartmentId(),
                departmentName,
                violation.getCameraSessionId(),
                violation.getViolationType(),
                violation.getConfidence()
                        .doubleValue(),
                violation.getModelVersion(),
                violation.getDetectedAt(),
                violation.getStartedAt(),
                violation.getEndedAt(),
                violation.getLifecycleStatus(),
                violation.getReviewStatus(),
                violation.getReviewedBy(),
                violation.getReviewedAt(),
                recording.recordingStatus(),
                recording.clipReady(),
                recording.playbackUrl(),
                coverImageKey,
                coverImageReady
        );
    }

    private ViolationListItem toListItem(
            ViolationJpaEntity violation
    ) {
        return new ViolationListItem(
                violation.getId(),
                violation.getCameraId(),
                violation.getDepartmentId(),
                violation.getViolationType(),
                violation.getStartedAt(),
                violation.getEndedAt(),
                violation.getConfidence()
                        .doubleValue(),
                violation.getLifecycleStatus(),
                violation.getReviewStatus()
        );
    }

    private String resolveDepartmentName(
            UUID departmentId
    ) {
        DepartmentNameResolver resolver =
                departmentNameResolverProvider.getIfAvailable();

        if (resolver == null) {
            return null;
        }

        return resolver.resolveDepartmentName(
                departmentId
        );
    }

    private RecordingQueryResult resolveRecording(
            ViolationJpaEntity violation
    ) {
        RecordingQueryPort recordingQueryPort =
                recordingQueryPortProvider.getIfAvailable();

        if (recordingQueryPort != null) {
            Optional<RecordingQueryResult> result =
                    recordingQueryPort.findByViolationId(
                            violation.getId()
                    );

            if (result.isPresent()) {
                return result.get();
            }
        }

        /*
         * BE-4 query adapter henüz bağlı değilse
         * BE-3 lifecycle bilgisinden yalnızca hazır/durum
         * bilgisi türetilir. Playback URL üretilmez.
         */
        if (violation.getLifecycleStatus()
                == ViolationLifecycleStatus.COMPLETED) {
            return new RecordingQueryResult(
                    "READY",
                    true,
                    null
            );
        }

        if (violation.getLifecycleStatus()
                == ViolationLifecycleStatus.ERROR) {
            return RecordingQueryResult.notReady(
                    "ERROR"
            );
        }

        return RecordingQueryResult.notReady(
                "REQUESTED"
        );
    }
}