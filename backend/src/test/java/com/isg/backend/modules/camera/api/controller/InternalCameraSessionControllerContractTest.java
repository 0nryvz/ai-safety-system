package com.isg.backend.modules.camera.api.controller;

import com.isg.backend.modules.camera.api.dto.CameraSessionRequest;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.modules.auth.infrastructure.InternalApiKeyFilter;
import com.isg.backend.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InternalCameraSessionControllerContractTest {

    private static final String INTERNAL_API_KEY =
            "test-internal-api-key";

    private static final String INTERNAL_API_KEY_PROPERTY =
            "application.security.internal.api-key";

    @Mock
    private CameraService cameraService;

    @Mock
    private Environment environment;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(environment.getProperty(INTERNAL_API_KEY_PROPERTY))
                .thenReturn(INTERNAL_API_KEY);

        InternalApiKeyFilter internalApiKeyFilter =
                new InternalApiKeyFilter(environment);

        InternalCameraSessionController controller =
                new InternalCameraSessionController(cameraService);

        Clock clock = Clock.fixed(
                Instant.parse("2026-08-20T00:00:00Z"),
                ZoneOffset.UTC
        );

        GlobalExceptionHandler exceptionHandler =
                new GlobalExceptionHandler(clock);

        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(exceptionHandler)
                .addFilters(internalApiKeyFilter)
                .build();
    }

    @Test
    void openWithValidInternalKeyAndValidUuidsReturnsOk()
            throws Exception {

        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/open")
                                .header(
                                        "X-Internal-Api-Key",
                                        INTERNAL_API_KEY
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "%s",
                                          "sessionId": "%s",
                                          "deviceInfo": "gateway-contract-test"
                                        }
                                        """.formatted(
                                        cameraId,
                                        sessionId
                                ))
                )
                .andExpect(status().isOk());

        verify(cameraService).openSession(
                argThat(request ->
                        cameraId.equals(request.getCameraId())
                                && sessionId.toString()
                                .equals(request.getSessionId())
                                && "gateway-contract-test"
                                .equals(request.getDeviceInfo())
                )
        );
    }

    @Test
    void heartbeatWithValidInternalKeyAndValidUuidsReturnsOk()
            throws Exception {

        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/heartbeat")
                                .header(
                                        "X-Internal-Api-Key",
                                        INTERNAL_API_KEY
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "%s",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(
                                        cameraId,
                                        sessionId
                                ))
                )
                .andExpect(status().isOk());

        verify(cameraService).processHeartbeat(
                cameraId,
                sessionId.toString()
        );
    }

    @Test
    void closeWithValidInternalKeyAndValidUuidsReturnsOk()
            throws Exception {

        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/close")
                                .header(
                                        "X-Internal-Api-Key",
                                        INTERNAL_API_KEY
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "%s",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(
                                        cameraId,
                                        sessionId
                                ))
                )
                .andExpect(status().isOk());

        verify(cameraService).closeSession(
                cameraId,
                sessionId.toString()
        );
    }

    @Test
    void missingInternalApiKeyReturnsUnauthorized()
            throws Exception {

        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/open")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "%s",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(
                                        cameraId,
                                        sessionId
                                ))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        content().contentTypeCompatibleWith(
                                MediaType.APPLICATION_JSON
                        )
                )
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(cameraService, never())
                .openSession(any(CameraSessionRequest.class));
    }

    @Test
    void wrongInternalApiKeyReturnsUnauthorized()
            throws Exception {

        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/open")
                                .header(
                                        "X-Internal-Api-Key",
                                        "wrong-internal-api-key"
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "%s",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(
                                        cameraId,
                                        sessionId
                                ))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(cameraService, never())
                .openSession(any(CameraSessionRequest.class));
    }

    @Test
    void invalidSessionIdUuidReturnsValidationError()
            throws Exception {

        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/open")
                                .header(
                                        "X-Internal-Api-Key",
                                        INTERNAL_API_KEY
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "%s",
                                          "sessionId": "session-1"
                                        }
                                        """.formatted(cameraId))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/internal/v1/camera-sessions/open"
                                )
                )
                .andExpect(
                        jsonPath("$.fieldErrors.sessionId")
                                .exists()
                );

        verify(cameraService, never())
                .openSession(any(CameraSessionRequest.class));
    }

    @Test
    void invalidCameraIdUuidReturnsInvalidRequest()
            throws Exception {

        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/open")
                                .header(
                                        "X-Internal-Api-Key",
                                        INTERNAL_API_KEY
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "camera-1",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(sessionId))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.code")
                                .value("INVALID_REQUEST")
                )
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Malformed or invalid request body."
                                )
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/internal/v1/camera-sessions/open"
                                )
                );

        verify(cameraService, never())
                .openSession(any(CameraSessionRequest.class));
    }

    @Test
    void missingCameraIdReturnsValidationError()
            throws Exception {

        UUID sessionId = UUID.randomUUID();

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/open")
                                .header(
                                        "X-Internal-Api-Key",
                                        INTERNAL_API_KEY
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "sessionId": "%s"
                                        }
                                        """.formatted(sessionId))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.fieldErrors.cameraId")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/internal/v1/camera-sessions/open"
                                )
                );

        verify(cameraService, never())
                .openSession(any(CameraSessionRequest.class));
    }

    @Test
    void missingSessionIdReturnsValidationError()
            throws Exception {

        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/open")
                                .header(
                                        "X-Internal-Api-Key",
                                        INTERNAL_API_KEY
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "%s"
                                        }
                                        """.formatted(cameraId))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.fieldErrors.sessionId")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/internal/v1/camera-sessions/open"
                                )
                );

        verify(cameraService, never())
                .openSession(any(CameraSessionRequest.class));
    }

    @Test
    void deviceInfoLongerThan255CharactersReturnsValidationError()
            throws Exception {

        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        String tooLongDeviceInfo =
                "x".repeat(256);

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/open")
                                .header(
                                        "X-Internal-Api-Key",
                                        INTERNAL_API_KEY
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "%s",
                                          "sessionId": "%s",
                                          "deviceInfo": "%s"
                                        }
                                        """.formatted(
                                        cameraId,
                                        sessionId,
                                        tooLongDeviceInfo
                                ))
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.fieldErrors.deviceInfo")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/internal/v1/camera-sessions/open"
                                )
                );

        verify(cameraService, never())
                .openSession(any(CameraSessionRequest.class));
    }

    @Test
    void serviceConflictReturnsHttp409()
            throws Exception {

        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        doThrow(
                new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Kamera icin farkli bir aktif session zaten mevcut"
                )
        )
                .when(cameraService)
                .openSession(any(CameraSessionRequest.class));

        mockMvc.perform(
                        post("/internal/v1/camera-sessions/open")
                                .header(
                                        "X-Internal-Api-Key",
                                        INTERNAL_API_KEY
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "cameraId": "%s",
                                          "sessionId": "%s"
                                        }
                                        """.formatted(
                                        cameraId,
                                        sessionId
                                ))
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(
                        jsonPath("$.code")
                                .value("CONFLICT")
                )
                .andExpect(
                        jsonPath("$.path")
                                .value(
                                        "/internal/v1/camera-sessions/open"
                                )
                );
    }
}