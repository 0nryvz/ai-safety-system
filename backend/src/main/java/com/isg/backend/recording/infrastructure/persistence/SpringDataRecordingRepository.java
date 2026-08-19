package com.isg.backend.recording.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SpringDataRecordingRepository
        extends JpaRepository<RecordingJpaEntity, UUID> {

    Optional<RecordingJpaEntity> findByViolationId(
            UUID violationId
    );

    List<RecordingJpaEntity> findByViolationIdIn(
            Collection<UUID> violationIds
    );

    @Query("""
            select r.violationId
            from RecordingJpaEntity r
            where r.status = :status
            """)
    Set<UUID> findViolationIdsByStatus(
            @Param("status") String status
    );
}