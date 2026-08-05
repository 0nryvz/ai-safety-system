package com.isg.backend.modules.camera.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CameraSessionRequest {

    @NotNull(message = "Kamera ID boş olamaz")
    private UUID cameraId;

    @NotBlank(message = "Oturum ID (Session ID) boş olamaz")
    private String sessionId; // Entity ile uyumlu olması için String yapıldı

    private String deviceInfo;
}