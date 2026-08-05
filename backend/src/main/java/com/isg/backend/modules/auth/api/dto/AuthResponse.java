package com.isg.backend.modules.auth.api.dto;

public record AuthResponse(
        String accessToken,
        String refreshToken,
        String tokenType
) {
    // Hem access token hem refresh token alıp, tokenType'ı default "Bearer" yapan constructor
    public AuthResponse(String accessToken, String refreshToken) {
        this(accessToken, refreshToken, "Bearer");
    }
}