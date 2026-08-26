package com.isg.backend.camera.service;

import java.util.Optional;
import java.util.UUID;

public interface CameraQueryService {

    boolean isValid(
            UUID cameraId,
            UUID sessionId
    );

    Optional<UUID> findSessionRecordId(
            UUID cameraId,
            UUID sessionId
    );

    Optional<UUID> findGatewaySessionId(
            UUID sessionRecordId
    );

    Optional<String> findCameraName(
            UUID cameraId
    );

    Optional<UUID> findDepartmentId(
            UUID cameraId
    );
}