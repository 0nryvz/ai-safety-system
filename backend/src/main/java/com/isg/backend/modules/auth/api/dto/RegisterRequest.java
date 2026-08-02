package com.isg.backend.modules.auth.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Ad alanı boş bırakılamaz")
        String firstName,

        @NotBlank(message = "Soyad alanı boş bırakılamaz")
        String lastName,

        @NotBlank(message = "E-posta alanı boş bırakılamaz")
        @Email(message = "Geçerli bir e-posta adresi giriniz")
        String email,

        @NotBlank(message = "Şifre alanı boş bırakılamaz")
        @Size(min = 8, message = "Şifre en az 8 karakter olmalıdır")
        String password
) {
}