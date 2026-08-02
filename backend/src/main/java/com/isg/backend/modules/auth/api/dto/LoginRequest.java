package com.isg.backend.modules.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank(message = "E-posta adresi boş bırakılamaz.")
        @Email(message = "Lütfen geçerli bir e-posta adresi giriniz.")
        String email,

        @NotBlank(message = "Şifre boş bırakılamaz.")
        String password
) {
}