package com.isg.backend.violation.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.application.port.RecordingQueryPort;
import com.isg.backend.violation.application.port.RecordingQueryResult;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.exception.ViolationNotFoundException;
import com.isg.backend.violation.exception.InvalidViolationQueryException;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationDetailProjection;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.infrastructure.persistence.ViolationSpecifications;
import com.isg.backend.violation.query.ViolationDetailResponse;
import com.isg.backend.violation.query.ViolationListItem;
import com.isg.backend.violation.query.ViolationQueryFilter;
import com.isg.backend.recording.domain.RecordingStatus;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ViolationQueryService {

    private final SpringDataViolationRepository violationRepository;
    private final AuthorizationService authorizationService;
    private final ObjectProvider<RecordingQueryPort> recordingQueryPortProvider;

    public ViolationQueryService(
            SpringDataViolationRepository violationRepository,
            AuthorizationService authorizationService,
            ObjectProvider<RecordingQueryPort> recordingQueryPortProvider
    ) {
        this.violationRepository =
                violationRepository;

        this.authorizationService =
                authorizationService;

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

        Set<UUID> recordingViolationIds =
                resolveRecordingFilterIds(
                        filter.recordingStatus()
                );

        Page<ViolationJpaEntity> violations =
                violationRepository.findAll(
                        ViolationSpecifications.fromFilter(
                                filter,
                                accessibleDepartmentIds,
                                recordingViolationIds
                        ),
                        pageable
                );

        Map<UUID, RecordingQueryResult> recordings =
                resolveRecordings(
                        violations.getContent()
                );

        return violations.map(
                violation -> toListItem(
                        violation,
                        recordings.get(
                                violation.getId()
                        )
                )
        );
    }

    private void validateRecordingStatus(
            String recordingStatus
    ) {
        if (recordingStatus == null) {
            return;
        }

        try {
            RecordingStatus.valueOf(
                    recordingStatus
            );
        } catch (IllegalArgumentException ex) {
            throw new InvalidViolationQueryException(
                    "Unsupported recordingStatus: "
                            + recordingStatus
            );
        }
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

        ViolationDetailProjection projection =
                violationRepository.findDetailProjectionById(
                        violationId
                ).orElseThrow(
                        () -> new ViolationNotFoundException(
                                violationId
                        )
                );

        boolean authorized =
                authorizationService.canAccessDepartment(
                        userId,
                        projection.getDepartmentId()
                );

        if (!authorized) {
            throw new ViolationNotFoundException(
                    violationId
            );
        }

        ViolationLifecycleStatus lifecycleStatus =
                ViolationLifecycleStatus.valueOf(
                        projection.getLifecycleStatus()
                );

        RecordingQueryResult recording =
                resolveRecording(
                        violationId
                );

        String coverImageKey =
                projection.getCoverImageKey();

        boolean coverImageReady =
                coverImageKey != null
                        && !coverImageKey.isBlank();

        return new ViolationDetailResponse(
                projection.getViolationId(),
                projection.getCameraId(),
                projection.getCameraName(),
                projection.getCameraCode(),
                projection.getDepartmentId(),
                projection.getDepartmentName(),
                projection.getSessionId(),
                ViolationType.valueOf(
                        projection.getType()
                ),
                projection.getConfidence()
                        .doubleValue(),
                projection.getModelVersion(),
                projection.getDetectedAt(),
                projection.getStartedAt(),
                projection.getEndedAt(),
                lifecycleStatus,
                ViolationReviewStatus.valueOf(
                        projection.getReviewStatus()
                ),
                projection.getReviewedBy(),
                projection.getReviewedAt(),
                recording != null
                        ? recording.recordingStatus()
                        : null,
                recording != null
                        && recording.clipReady(),
                recording != null
                        ? recording.playbackUrl()
                        : null,
                coverImageKey,
                coverImageReady,
                projection.getVersion()
        );
    }

    private ViolationListItem toListItem(
            ViolationJpaEntity violation,
            RecordingQueryResult recording
    ) {
        String recordingStatus =
                recording != null
                        ? recording.recordingStatus()
                        : null;

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
                violation.getReviewStatus(),
                recordingStatus,
                violation.getUpdatedAt()
        );
    }

    private Set<UUID> resolveRecordingFilterIds(
            String recordingStatus
    ) {
        if (recordingStatus == null) {
            return Set.of();
        }

        RecordingQueryPort recordingQueryPort =
                recordingQueryPortProvider.getIfAvailable();

        if (recordingQueryPort == null) {
            throw new IllegalStateException(
                    "Recording query adapter is required for recordingStatus filtering"
            );
        }

        try {
            Set<UUID> violationIds =
                    recordingQueryPort.findViolationIdsByRecordingStatus(
                            recordingStatus
                    );

            return Set.copyOf(
                    Objects.requireNonNull(
                            violationIds,
                            "recording violation ids must not be null"
                    )
            );

        } catch (IllegalArgumentException ex) {
            throw new InvalidViolationQueryException(
                    "Unsupported recordingStatus: "
                            + recordingStatus
            );
        }
    }

    private Map<UUID, RecordingQueryResult> resolveRecordings(
            List<ViolationJpaEntity> violations
    ) {
        if (violations.isEmpty()) {
            return Map.of();
        }

        RecordingQueryPort recordingQueryPort =
                recordingQueryPortProvider.getIfAvailable();

        if (recordingQueryPort == null) {
            return Map.of();
        }

        List<UUID> violationIds =
                violations.stream()
                        .map(
                                ViolationJpaEntity::getId
                        )
                        .toList();

        Map<UUID, RecordingQueryResult> recordings =
                recordingQueryPort.findByViolationIds(
                        violationIds
                );

        return Map.copyOf(
                Objects.requireNonNull(
                        recordings,
                        "recording results must not be null"
                )
        );
    }

    private RecordingQueryResult resolveRecording(
            UUID violationId
    ) {
        RecordingQueryPort recordingQueryPort =
                recordingQueryPortProvider.getIfAvailable();

        if (recordingQueryPort != null) {
            Optional<RecordingQueryResult> result =
                    recordingQueryPort.findByViolationId(
                            violationId
                    );

            if (result.isPresent()) {
                return result.get();
            }
        }

        return null;
    }
}
