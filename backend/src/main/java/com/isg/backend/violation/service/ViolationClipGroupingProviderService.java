package com.isg.backend.violation.service;

import com.isg.backend.violation.application.ViolationClipGroupingProvider;
import com.isg.backend.violation.application.ViolationClipGroupingView;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ViolationClipGroupingProviderService
        implements ViolationClipGroupingProvider {

    private final SpringDataViolationRepository violationRepository;

    public ViolationClipGroupingProviderService(
            SpringDataViolationRepository violationRepository
    ) {
        this.violationRepository = Objects.requireNonNull(
                violationRepository,
                "violationRepository cannot be null"
        );
    }

    @Override
    public Optional<ViolationClipGroupingView> findContext(
            UUID violationId
    ) {
        Objects.requireNonNull(
                violationId,
                "violationId cannot be null"
        );

        return violationRepository.findById(violationId)
                .filter(
                        violation ->
                                violation.getCameraSessionId() != null
                                        && violation.getSubjectKey() != null
                                        && violation.getSubjectKey().startsWith("track-")
                )
                .map(this::toGroupingView);
    }

    @Override
    public Set<UUID> findActiveViolationIds(
            ViolationClipGroupingView context
    ) {
        Objects.requireNonNull(
                context,
                "context cannot be null"
        );

        return Set.copyOf(
                violationRepository
                        .findIdsByGroupingContextAndLifecycleStatus(
                                context.cameraId(),
                                context.cameraSessionId(),
                                context.subjectKey(),
                                ViolationLifecycleStatus.ACTIVE
                        )
        );
    }

    private ViolationClipGroupingView toGroupingView(
            ViolationJpaEntity violation
    ) {
        return new ViolationClipGroupingView(
                violation.getCameraId(),
                violation.getCameraSessionId(),
                violation.getSubjectKey()
        );
    }
}