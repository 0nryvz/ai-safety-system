package com.isg.backend.modules.camera.api.dto;

import lombok.Data;
import java.util.UUID;


@Data
public class CameraUpdateRequest {
    private String name;
    private String code;
    private UUID departmentId;
    private Boolean active; // Soft-delete veya tekrar aktif etme işlemi için
}