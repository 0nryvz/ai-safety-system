package com.isg.backend.modules.user.infrastructure;

import com.isg.backend.modules.user.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID; // DÜZELTME: UUID import edildi

@Repository
public interface DepartmentRepository extends JpaRepository<Department, UUID> { // DÜZELTME: Long yerine UUID yazıldı
    Optional<Department> findByName(String name);
    boolean existsByName(String name);
}