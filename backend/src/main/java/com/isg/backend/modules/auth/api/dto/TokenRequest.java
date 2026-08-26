package com.isg.backend.modules.auth.api.dto;

import jakarta.validation.constraints.NotBlank;

public record TokenRequest(

        @NotBlank(message = "Refresh token boş olamaz")
        String refreshToken

) {
}