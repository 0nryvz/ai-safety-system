package com.isg.backend.modules.camera.api.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CameraSessionRequest {
    private UUID cameraId;
    private String sessionId; // Entity ile uyumlu olması için String yapıldı
    private String deviceInfo;
}