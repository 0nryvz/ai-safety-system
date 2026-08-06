package com.isg.backend.modules.camera.api.dto;

import lombok.Data;

import java.util.UUID; // DÜZELTME: Bu importu kesinlikle geri eklemeliyiz

@Data
public class CameraUpdateRequest {
    private String name;
    private String code;

    // DÜZELTME: Long yerine tekrar UUID yapıldı
    private UUID departmentId;

    private Boolean active; // Soft-delete veya tekrar aktif etme işlemi için
}