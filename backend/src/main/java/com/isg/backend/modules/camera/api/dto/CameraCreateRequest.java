package com.isg.backend.modules.camera.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID; // Bu importu geri ekliyoruz

@Data
public class CameraCreateRequest {
    @NotBlank(message = "Kamera adı boş bırakılamaz")
    private String name;

    @NotBlank(message = "Kamera kodu boş bırakılamaz")
    private String code;

    @NotNull(message = "Kameranın atanacağı departman ID zorunludur")
    private UUID departmentId; // DÜZELTME: Long yerine kesinlikle UUID olmalı
}