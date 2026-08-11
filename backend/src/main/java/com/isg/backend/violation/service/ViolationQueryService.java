package com.isg.backend.violation.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.infrastructure.persistence.ViolationSpecifications;
import com.isg.backend.violation.query.ViolationListItem;
import com.isg.backend.violation.query.ViolationQueryFilter;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ViolationQueryService {

    private final SpringDataViolationRepository violationRepository;
    private final AuthorizationService authorizationService;

    public ViolationQueryService(
            SpringDataViolationRepository violationRepository,
            AuthorizationService authorizationService
    ) {
        this.violationRepository =
                violationRepository;

        this.authorizationService =
                authorizationService;
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
}