package com.isg.backend.modules.camera.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class CameraSessionRequest {

    @NotNull(message = "cameraId zorunludur")
    private UUID cameraId;

    @NotBlank(message = "sessionId zorunludur")
    @Pattern(
            regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "sessionId geçerli bir UUID formatında olmalıdır"
    )
    private String sessionId;

    @Size(max = 255, message = "deviceInfo en fazla 255 karakter olabilir")
    private String deviceInfo;
}