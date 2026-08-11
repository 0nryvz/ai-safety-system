package com.isg.backend.violation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SpringDataViolationStatusHistoryRepository
        extends JpaRepository<ViolationStatusHistoryJpaEntity, UUID> {
}