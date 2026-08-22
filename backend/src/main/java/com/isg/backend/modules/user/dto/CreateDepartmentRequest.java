package com.isg.backend.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDepartmentRequest {

    @NotBlank(message = "Departman kodu zorunludur.")
    @Size(max = 40, message = "Departman kodu en fazla 40 karakter olabilir.")
    private String code;

    @NotBlank(message = "Departman adı zorunludur.")
    @Size(max = 120, message = "Departman adı en fazla 120 karakter olabilir.")
    private String name;

    @Size(max = 500, message = "Departman açıklaması en fazla 500 karakter olabilir.")
    private String description;
}