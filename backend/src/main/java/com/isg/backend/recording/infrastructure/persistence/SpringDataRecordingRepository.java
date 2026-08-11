package com.isg.backend.recording.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SpringDataRecordingRepository extends JpaRepository<RecordingJpaEntity, UUID> {
    Optional<RecordingJpaEntity> findByViolationId(UUID violationId);
}
