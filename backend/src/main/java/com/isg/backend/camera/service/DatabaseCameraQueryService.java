package com.isg.backend.camera.service;

import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.domain.entity.CameraSession;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.camera.infrastructure.repository.CameraSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class DatabaseCameraQueryService
        implements CameraQueryService {

    private final CameraSessionRepository cameraSessionRepository;
    private final CameraRepository cameraRepository;

    public DatabaseCameraQueryService(
            CameraSessionRepository cameraSessionRepository,
            CameraRepository cameraRepository
    ) {
        this.cameraSessionRepository =
                cameraSessionRepository;

        this.cameraRepository =
                cameraRepository;
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

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findGatewaySessionId(
            UUID sessionRecordId
    ) {
        if (sessionRecordId == null) {
            return Optional.empty();
        }

        return cameraSessionRepository
                .findById(
                        sessionRecordId
                )
                .map(CameraSession::getSessionId)
                .flatMap(
                        this::parseUuid
                );
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> findCameraName(
            UUID cameraId
    ) {
        if (cameraId == null) {
            return Optional.empty();
        }

        return cameraRepository
                .findById(cameraId)
                .map(Camera::getName);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> findDepartmentId(
            UUID cameraId
    ) {
        if (cameraId == null) {
            return Optional.empty();
        }

        return cameraRepository
                .findById(cameraId)
                .filter(camera ->
                        camera.getDepartment() != null
                )
                .map(camera ->
                        camera.getDepartment().getId()
                );
    }

    private Optional<UUID> parseUuid(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    UUID.fromString(
                            value
                    )
            );
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
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