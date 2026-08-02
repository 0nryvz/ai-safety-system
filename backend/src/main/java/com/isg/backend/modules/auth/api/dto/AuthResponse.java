package com.isg.backend.modules.auth.api.dto;

public record AuthResponse(
        String accessToken,
        String tokenType
) {
    public AuthResponse(String accessToken) {
        this(accessToken, "Bearer"); // Default olarak Bearer atıyoruz
    }
}