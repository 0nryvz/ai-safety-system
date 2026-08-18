package com.isg.backend.violation.controller;

import com.isg.backend.modules.user.dto.UserResponse;
import com.isg.backend.modules.user.service.UserService;
import com.isg.backend.recording.application.RecordingNotFoundForViolationException;
import com.isg.backend.recording.application.RecordingNotReadyException;
import com.isg.backend.recording.application.port.PresignedPlaybackUrl;
import com.isg.backend.recording.domain.RecordingStatus;
import com.isg.backend.shared.web.GlobalExceptionHandler;
import com.isg.backend.violation.exception.ViolationNotFoundException;
import com.isg.backend.violation.service.ViolationMediaAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ViolationMediaControllerTest {

    private ViolationMediaAccessService mediaAccessService;
    private UserService userService;
    private Authentication authentication;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mediaAccessService =
                mock(ViolationMediaAccessService.class);

        userService =
                mock(UserService.class);

        authentication =
                mock(Authentication.class);

        Clock clock =
                Clock.fixed(
                        Instant.parse(
                                "2026-08-18T18:00:00Z"
                        ),
                        ZoneOffset.UTC
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(
                                new ViolationMediaController(
                                        mediaAccessService,
                                        userService
                                )
                        )
                        .setControllerAdvice(
                                new GlobalExceptionHandler(
                                        clock
                                )
                        )
                        .build();
    }

    @Test
    void returns200WithPresignedClipUrl() throws Exception {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        when(
                authentication.getName()
        ).thenReturn(
                "user@example.com"
        );

        when(
                userService.getMe(
                        "user@example.com"
                )
        ).thenReturn(
                UserResponse.builder()
                        .id(userId)
                        .email("user@example.com")
                        .build()
        );

        when(
                mediaAccessService.createClipUrl(
                        userId,
                        violationId
                )
        ).thenReturn(
                new PresignedPlaybackUrl(
                        "http://localhost:9000/presigned",
                        Instant.parse(
                                "2026-08-18T18:05:00Z"
                        )
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/violations/{violationId}/clip-url",
                                violationId
                        )
                                .principal(
                                        authentication
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.url")
                                .value(
                                        "http://localhost:9000/presigned"
                                )
                )
                .andExpect(
                        jsonPath("$.expiresAt")
                                .value(
                                        "2026-08-18T18:05:00Z"
                                )
                );
    }

    @Test
    void returns403WhenDepartmentAccessIsDenied() throws Exception {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        mockAuthenticatedUser(
                userId
        );

        when(
                mediaAccessService.createClipUrl(
                        userId,
                        violationId
                )
        ).thenThrow(
                new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You do not have access to this violation."
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/violations/{violationId}/clip-url",
                                violationId
                        )
                                .principal(
                                        authentication
                                )
                )
                .andExpect(
                        status().isForbidden()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "FORBIDDEN"
                                )
                );
    }

    @Test
    void returns404WhenViolationDoesNotExist() throws Exception {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        mockAuthenticatedUser(
                userId
        );

        when(
                mediaAccessService.createClipUrl(
                        userId,
                        violationId
                )
        ).thenThrow(
                new ViolationNotFoundException(
                        violationId
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/violations/{violationId}/clip-url",
                                violationId
                        )
                                .principal(
                                        authentication
                                )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "VIOLATION_NOT_FOUND"
                                )
                );
    }

    @Test
    void returns404WhenRecordingDoesNotExist() throws Exception {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        mockAuthenticatedUser(
                userId
        );

        when(
                mediaAccessService.createClipUrl(
                        userId,
                        violationId
                )
        ).thenThrow(
                new RecordingNotFoundForViolationException(
                        violationId
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/violations/{violationId}/clip-url",
                                violationId
                        )
                                .principal(
                                        authentication
                                )
                )
                .andExpect(
                        status().isNotFound()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "RECORDING_NOT_FOUND"
                                )
                );
    }

    @Test
    void returns409WhenRecordingIsNotReady() throws Exception {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        mockAuthenticatedUser(
                userId
        );

        when(
                mediaAccessService.createClipUrl(
                        userId,
                        violationId
                )
        ).thenThrow(
                new RecordingNotReadyException(
                        violationId,
                        RecordingStatus.PROCESSING
                )
        );

        mockMvc.perform(
                        get(
                                "/api/v1/violations/{violationId}/clip-url",
                                violationId
                        )
                                .principal(
                                        authentication
                                )
                )
                .andExpect(
                        status().isConflict()
                )
                .andExpect(
                        jsonPath("$.code")
                                .value(
                                        "RECORDING_NOT_READY"
                                )
                );
    }

    private void mockAuthenticatedUser(
            UUID userId
    ) {
        when(
                authentication.getName()
        ).thenReturn(
                "user@example.com"
        );

        when(
                userService.getMe(
                        "user@example.com"
                )
        ).thenReturn(
                UserResponse.builder()
                        .id(userId)
                        .email("user@example.com")
                        .build()
        );
    }
}