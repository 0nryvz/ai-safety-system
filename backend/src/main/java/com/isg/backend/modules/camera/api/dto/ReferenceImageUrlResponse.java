package com.isg.backend.modules.camera.api.dto;

import java.time.Instant;

public record ReferenceImageUrlResponse(
        String url,
        Instant expiresAt
) {
}