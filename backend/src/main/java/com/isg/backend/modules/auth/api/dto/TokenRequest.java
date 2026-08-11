package com.isg.backend.modules.auth.api.dto;

public record TokenRequest(
        String refreshToken
) {
}