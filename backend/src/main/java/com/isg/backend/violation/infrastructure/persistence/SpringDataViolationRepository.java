package com.isg.backend.violation.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface SpringDataViolationRepository
        extends JpaRepository<ViolationJpaEntity, UUID>,
        JpaSpecificationExecutor<ViolationJpaEntity> {
}