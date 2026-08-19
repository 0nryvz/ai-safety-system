package com.isg.backend.shared.web;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GlobalExceptionHandlerTest {

    private final Clock clock =
            Clock.fixed(
                    Instant.parse("2026-08-17T20:00:00Z"),
                    ZoneOffset.UTC
            );

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler(clock);

    @Test
    void mapsConflictResponseStatusExceptionToConflictCode() {
        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "POST",
                        "/internal/v1/detections"
                );

        ResponseStatusException exception =
                new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Duplicate detection event."
                );

        ResponseEntity<ApiErrorResponse> response =
                handler.responseStatus(
                        exception,
                        request
                );

        assertEquals(
                HttpStatus.CONFLICT,
                response.getStatusCode()
        );

        ApiErrorResponse body =
                response.getBody();

        assertNotNull(body);
        assertEquals(409, body.status());
        assertEquals("CONFLICT", body.code());
        assertEquals(
                "Duplicate detection event.",
                body.message()
        );
        assertEquals(
                "/internal/v1/detections",
                body.path()
        );
        assertEquals(
                Instant.parse("2026-08-17T20:00:00Z"),
                body.timestamp()
        );
    }
}