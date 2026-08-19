package com.isg.backend.violation.controller;

import java.time.Instant;

public record MediaUrlResponse(
        String url,
        Instant expiresAt
) {
}