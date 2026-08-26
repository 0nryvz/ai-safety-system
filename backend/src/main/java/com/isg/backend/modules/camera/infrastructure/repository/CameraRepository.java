package com.isg.backend.modules.camera.infrastructure.repository;

import com.isg.backend.modules.camera.domain.entity.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CameraRepository extends JpaRepository<Camera, UUID> {
    Optional<Camera> findByCode(String code);
    List<Camera> findByDepartmentId(UUID departmentId);
    List<Camera> findByDepartmentIdAndActiveTrue(UUID departmentId);
    List<Camera> findByDepartmentIdIn(List<UUID> departmentIds); // Eklendi
}