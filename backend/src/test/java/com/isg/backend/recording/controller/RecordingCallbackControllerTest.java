package com.isg.backend.recording.controller;

import com.isg.backend.recording.application.RecordingApplicationService;
import com.isg.backend.recording.application.RecordingCallbackConflictException;
import com.isg.backend.recording.application.RecordingNotFoundException;
import com.isg.backend.modules.auth.infrastructure.InternalApiKeyFilter;
import com.isg.backend.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RecordingCallbackControllerTest {

    private static final String INTERNAL_API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_API_KEY = "test-internal-api-key";

    private MockMvc mockMvc;

    private RecordingApplicationService recordingApplicationService;

    @BeforeEach
    void setUp() {
        recordingApplicationService = mock(RecordingApplicationService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new RecordingCallbackController(recordingApplicationService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .addFilters(new InternalApiKeyFilter(
                        new MockEnvironment().withProperty(
                                "application.security.internal.api-key",
                                INTERNAL_API_KEY
                        )
                ))
                .build();
    }

    @Test
    void validReadyCallbackReturns202() throws Exception {
        mockMvc.perform(
                        post("/internal/v1/recordings/callback")
                                .header(INTERNAL_API_KEY_HEADER, INTERNAL_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validReadyRequest())
                )
                .andExpect(status().isAccepted());

        verify(recordingApplicationService).handleCallback(any());
    }

    @Test
    void unknownRecordingReturns404() throws Exception {
        UUID recordingId = UUID.randomUUID();
        doThrow(new RecordingNotFoundException(recordingId))
                .when(recordingApplicationService)
                .handleCallback(any());

        mockMvc.perform(
                        post("/internal/v1/recordings/callback")
                                .header(INTERNAL_API_KEY_HEADER, INTERNAL_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validReadyRequest(recordingId, UUID.randomUUID()))
                )
                .andExpect(status().isNotFound());
    }

    @Test
    void violationMismatchReturns409() throws Exception {
        doThrow(new RecordingCallbackConflictException("Violation mismatch"))
                .when(recordingApplicationService)
                .handleCallback(any());

        mockMvc.perform(
                        post("/internal/v1/recordings/callback")
                                .header(INTERNAL_API_KEY_HEADER, INTERNAL_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validReadyRequest())
                )
                .andExpect(status().isConflict());
    }

    @Test
    void missingInternalApiKeyReturns401() throws Exception {
        mockMvc.perform(
                        post("/internal/v1/recordings/callback")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validReadyRequest())
                )
                .andExpect(status().isUnauthorized());

        verify(recordingApplicationService, never()).handleCallback(any());
    }

    @Test
    void invalidInternalApiKeyReturns401() throws Exception {
        mockMvc.perform(
                        post("/internal/v1/recordings/callback")
                                .header(INTERNAL_API_KEY_HEADER, "invalid")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(validReadyRequest())
                )
                .andExpect(status().isUnauthorized());

        verify(recordingApplicationService, never()).handleCallback(any());
    }

    @Test
    void invalidPayloadReturns400() throws Exception {
        String payload = """
                {
                  "violationId": "%s",
                  "status": "READY",
                  "objectKey": "clips/object.mp4",
                  "durationMs": 2000,
                  "sizeBytes": 4000,
                  "checksum": "sha256:abc",
                  "retryCount": 1
                }
                """.formatted(UUID.randomUUID());

        mockMvc.perform(
                        post("/internal/v1/recordings/callback")
                                .header(INTERNAL_API_KEY_HEADER, INTERNAL_API_KEY)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isBadRequest());

        verify(recordingApplicationService, never()).handleCallback(any());
    }

    private String validReadyRequest() {
        return validReadyRequest(UUID.randomUUID(), UUID.randomUUID());
    }

    private String validReadyRequest(
            UUID recordingId,
            UUID violationId
    ) {
        return """
                {
                  "recordingId": "%s",
                  "violationId": "%s",
                  "status": "READY",
                  "objectKey": "clips/object.mp4",
                  "durationMs": 2000,
                  "sizeBytes": 4000,
                  "checksum": "sha256:abc",
                  "retryCount": 1,
                  "errorCode": null
                }
                """.formatted(recordingId, violationId);
    }
}
