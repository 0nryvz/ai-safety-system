package com.isg.backend.modules.camera.infrastructure.repository;

import com.isg.backend.modules.camera.domain.entity.CameraSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CameraSessionRepository extends JpaRepository<CameraSession, UUID> {
    Optional<CameraSession> findByCameraIdAndStatus(UUID cameraId, CameraSession.SessionStatus status);
    Optional<CameraSession> findBySessionId(String sessionId);

    // YENİ EKLENEN METOT: Sadece aktif statüdeki oturumları getirmek için
    List<CameraSession> findByStatus(CameraSession.SessionStatus status);
}