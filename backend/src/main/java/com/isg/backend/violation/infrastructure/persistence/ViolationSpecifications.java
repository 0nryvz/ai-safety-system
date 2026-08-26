package com.isg.backend.violation.infrastructure.persistence;

import com.isg.backend.violation.query.ViolationQueryFilter;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class ViolationSpecifications {

    private ViolationSpecifications() {
    }

    public static Specification<ViolationJpaEntity> fromFilter(
            ViolationQueryFilter filter,
            Collection<UUID> accessibleDepartmentIds
    ) {
        return fromFilter(
                filter,
                accessibleDepartmentIds,
                Set.of()
        );
    }

    public static Specification<ViolationJpaEntity> fromFilter(
            ViolationQueryFilter filter,
            Collection<UUID> accessibleDepartmentIds,
            Collection<UUID> recordingViolationIds
    ) {
        Objects.requireNonNull(
                filter,
                "filter must not be null"
        );

        Objects.requireNonNull(
                accessibleDepartmentIds,
                "accessibleDepartmentIds must not be null"
        );

        Objects.requireNonNull(
                recordingViolationIds,
                "recordingViolationIds must not be null"
        );

        return Specification.allOf(
                departmentAccess(
                        accessibleDepartmentIds
                ),
                startedAtGreaterThanOrEqual(
                        filter
                ),
                startedAtLessThanOrEqual(
                        filter
                ),
                hasViolationType(
                        filter
                ),
                hasCameraId(
                        filter
                ),
                hasDepartmentId(
                        filter
                ),
                hasLifecycleStatus(
                        filter
                ),
                hasReviewStatus(
                        filter
                ),
                hasRecordingStatus(
                        filter,
                        recordingViolationIds
                )
        );
    }

    private static Specification<ViolationJpaEntity> departmentAccess(
            Collection<UUID> accessibleDepartmentIds
    ) {
        if (accessibleDepartmentIds.isEmpty()) {
            return (
                    root,
                    query,
                    criteriaBuilder
            ) -> criteriaBuilder.disjunction();
        }

        return (
                root,
                query,
                criteriaBuilder
        ) -> root.get(
                "departmentId"
        ).in(
                accessibleDepartmentIds
        );
    }

    private static Specification<ViolationJpaEntity> startedAtGreaterThanOrEqual(
            ViolationQueryFilter filter
    ) {
        if (filter.from() == null) {
            return Specification.unrestricted();
        }

        return (
                root,
                query,
                criteriaBuilder
        ) -> criteriaBuilder.greaterThanOrEqualTo(
                root.get(
                        "startedAt"
                ),
                filter.from()
        );
    }

    private static Specification<ViolationJpaEntity> startedAtLessThanOrEqual(
            ViolationQueryFilter filter
    ) {
        if (filter.to() == null) {
            return Specification.unrestricted();
        }

        return (
                root,
                query,
                criteriaBuilder
        ) -> criteriaBuilder.lessThanOrEqualTo(
                root.get(
                        "startedAt"
                ),
                filter.to()
        );
    }

    private static Specification<ViolationJpaEntity> hasViolationType(
            ViolationQueryFilter filter
    ) {
        if (filter.type() == null) {
            return Specification.unrestricted();
        }

        return (
                root,
                query,
                criteriaBuilder
        ) -> criteriaBuilder.equal(
                root.get(
                        "violationType"
                ),
                filter.type()
        );
    }

    private static Specification<ViolationJpaEntity> hasCameraId(
            ViolationQueryFilter filter
    ) {
        if (filter.cameraId() == null) {
            return Specification.unrestricted();
        }

        return (
                root,
                query,
                criteriaBuilder
        ) -> criteriaBuilder.equal(
                root.get(
                        "cameraId"
                ),
                filter.cameraId()
        );
    }

    private static Specification<ViolationJpaEntity> hasDepartmentId(
            ViolationQueryFilter filter
    ) {
        if (filter.departmentId() == null) {
            return Specification.unrestricted();
        }

        return (
                root,
                query,
                criteriaBuilder
        ) -> criteriaBuilder.equal(
                root.get(
                        "departmentId"
                ),
                filter.departmentId()
        );
    }

    private static Specification<ViolationJpaEntity> hasLifecycleStatus(
            ViolationQueryFilter filter
    ) {
        if (filter.lifecycleStatus() == null) {
            return Specification.unrestricted();
        }

        return (
                root,
                query,
                criteriaBuilder
        ) -> criteriaBuilder.equal(
                root.get(
                        "lifecycleStatus"
                ),
                filter.lifecycleStatus()
        );
    }

    private static Specification<ViolationJpaEntity> hasReviewStatus(
            ViolationQueryFilter filter
    ) {
        if (filter.reviewStatus() == null) {
            return Specification.unrestricted();
        }

        return (
                root,
                query,
                criteriaBuilder
        ) -> criteriaBuilder.equal(
                root.get(
                        "reviewStatus"
                ),
                filter.reviewStatus()
        );
    }

    private static Specification<ViolationJpaEntity> hasRecordingStatus(
            ViolationQueryFilter filter,
            Collection<UUID> recordingViolationIds
    ) {
        if (filter.recordingStatus() == null) {
            return Specification.unrestricted();
        }

        if (recordingViolationIds.isEmpty()) {
            return (
                    root,
                    query,
                    criteriaBuilder
            ) -> criteriaBuilder.disjunction();
        }

        return (
                root,
                query,
                criteriaBuilder
        ) -> root.get(
                "id"
        ).in(
                recordingViolationIds
        );
    }
}
