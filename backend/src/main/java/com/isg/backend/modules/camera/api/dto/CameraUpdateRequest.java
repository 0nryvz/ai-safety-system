package com.isg.backend.modules.camera.api.dto;

import lombok.Data;

// java.util.UUID importunu sildik

@Data
public class CameraUpdateRequest {
    private String name;
    private String code;
    private Long departmentId; // UUID yerine Long olarak değiştirdik
    private Boolean active; // Soft-delete veya tekrar aktif etme işlemi için
}