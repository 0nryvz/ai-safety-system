package com.isg.backend.camera.service;

import com.isg.backend.modules.camera.domain.entity.CameraSession;
import com.isg.backend.modules.camera.infrastructure.repository.CameraSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DatabaseCameraQueryService
        implements CameraQueryService {

    private final CameraSessionRepository cameraSessionRepository;

    public DatabaseCameraQueryService(
            CameraSessionRepository cameraSessionRepository
    ) {
        this.cameraSessionRepository =
                cameraSessionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isValid(
            UUID cameraId,
            UUID sessionId
    ) {
        return findActiveSession(
                cameraId,
                sessionId
        ).isPresent();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findSessionRecordId(
            UUID cameraId,
            UUID sessionId
    ) {
        return findActiveSession(
                cameraId,
                sessionId
        ).map(
                CameraSession::getId
        );
    }

    private Optional<CameraSession> findActiveSession(
            UUID cameraId,
            UUID sessionId
    ) {
        if (cameraId == null || sessionId == null) {
            return Optional.empty();
        }

        return cameraSessionRepository
                .findBySessionId(
                        sessionId.toString()
                )
                .filter(session ->
                        session.getStatus()
                                == CameraSession.SessionStatus.ACTIVE
                )
                .filter(session ->
                        session.getCamera() != null
                                && cameraId.equals(
                                session.getCamera().getId()
                        )
                );
    }
}