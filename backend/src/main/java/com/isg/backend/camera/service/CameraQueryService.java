package com.isg.backend.camera.service;

import java.util.UUID;

public interface CameraQueryService {

    boolean isValid(UUID cameraId, UUID sessionId);

}
