package com.isg.backend.shared.web;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId,
        Map<String, String> fieldErrors
) {
}