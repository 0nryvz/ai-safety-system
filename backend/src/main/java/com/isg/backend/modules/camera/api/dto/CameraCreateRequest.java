package com.isg.backend.modules.camera.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

// java.util.UUID importunu sildik çünkü artık kullanmıyoruz

@Data
public class CameraCreateRequest {
    @NotBlank(message = "Kamera adı boş bırakılamaz")
    private String name;

    @NotBlank(message = "Kamera kodu boş bırakılamaz")
    private String code;

    @NotNull(message = "Kameranın atanacağı departman ID zorunludur")
    private Long departmentId; // UUID yerine Long olarak değiştirdik
}