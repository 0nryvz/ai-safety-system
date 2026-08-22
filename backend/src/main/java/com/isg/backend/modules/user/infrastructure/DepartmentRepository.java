package com.isg.backend.modules.user.infrastructure;

import com.isg.backend.modules.user.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> {
    Optional<Department> findByName(String name);
    boolean existsByName(String name);
    boolean existsByCode(String code);
}