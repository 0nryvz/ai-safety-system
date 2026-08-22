package com.isg.backend.violation.infrastructure.persistence;

import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface SpringDataViolationRepository
        extends JpaRepository<ViolationJpaEntity, UUID>,
        JpaSpecificationExecutor<ViolationJpaEntity> {

    List<ViolationJpaEntity> findByLifecycleStatusIn(
            Collection<ViolationLifecycleStatus> statuses
    );

    @Query("""
            select v.id
            from ViolationJpaEntity v
            where v.cameraId = :cameraId
              and v.cameraSessionId = :cameraSessionId
              and v.subjectKey = :subjectKey
              and v.lifecycleStatus = :lifecycleStatus
            """)
    Set<UUID> findIdsByGroupingContextAndLifecycleStatus(
            @Param("cameraId") UUID cameraId,
            @Param("cameraSessionId") UUID cameraSessionId,
            @Param("subjectKey") String subjectKey,
            @Param("lifecycleStatus") ViolationLifecycleStatus lifecycleStatus
    );
    @Query(
            value = """
                    SELECT
                        v.id AS "violationId",
                        v.camera_id AS "cameraId",
                        c.name AS "cameraName",
                        c.code AS "cameraCode",
                        v.department_id AS "departmentId",
                        d.name AS "departmentName",
                        v.camera_session_id AS "sessionId",
                        v.violation_type AS "type",
                        v.confidence AS "confidence",
                        v.model_version AS "modelVersion",
                        v.detected_at AS "detectedAt",
                        v.started_at AS "startedAt",
                        v.ended_at AS "endedAt",
                        v.lifecycle_status AS "lifecycleStatus",
                        v.review_status AS "reviewStatus",
                        v.reviewed_by AS "reviewedBy",
                        v.reviewed_at AS "reviewedAt",
                        v.cover_image_key AS "coverImageKey",
                        v.version AS "version"
                    FROM violations v
                    JOIN cameras c
                        ON c.id = v.camera_id
                    JOIN departments d
                        ON d.id = v.department_id
                    WHERE v.id = :violationId
                    """,
            nativeQuery = true
    )
    Optional<ViolationDetailProjection> findDetailProjectionById(
            @Param("violationId")
            UUID violationId
    );
}