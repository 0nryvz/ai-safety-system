package com.isg.backend.modules.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "Email boş olamaz")
        @Email(message = "Geçerli bir email formatı giriniz")
        String email,

        @NotBlank(message = "Şifre boş olamaz")
        String password
) {}