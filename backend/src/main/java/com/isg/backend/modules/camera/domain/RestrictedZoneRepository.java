package com.isg.backend.modules.camera.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface RestrictedZoneRepository extends JpaRepository<RestrictedZone, UUID> {
    Optional<RestrictedZone> findByCameraIdAndActiveTrue(UUID cameraId);
}