package com.isg.backend.violation.controller;

import com.isg.backend.violation.service.DetectionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        properties = {
                "application.security.internal.api-key=test-internal-api-key"
        }
)
@AutoConfigureMockMvc
class DetectionControllerTest {

    private static final String INTERNAL_API_KEY_HEADER =
            "X-Internal-Api-Key";

    private static final String INTERNAL_API_KEY =
            "test-internal-api-key";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DetectionService detectionService;

    @Test
    void validRequestReturns202() throws Exception {
        String requestBody = """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "cameraId": "22222222-2222-2222-2222-222222222222",
                  "sessionId": "33333333-3333-3333-3333-333333333333",
                  "frameTimestamp": "2026-08-06T20:00:00Z",
                  "modelVersion": "welding-ppe-v1",
                  "inferenceMs": 40,
                  "detections": [
                    {
                      "label": "welding_mask",
                      "confidence": 0.90,
                      "bbox": {
                        "x": 0.10,
                        "y": 0.10,
                        "width": 0.20,
                        "height": 0.20
                      }
                    }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/internal/v1/detections")
                                .header(
                                        INTERNAL_API_KEY_HEADER,
                                        INTERNAL_API_KEY
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isAccepted()
                );

        verify(
                detectionService
        ).process(
                any()
        );
    }

    @Test
    void missingCameraIdReturns400() throws Exception {
        String requestBody = """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "sessionId": "33333333-3333-3333-3333-333333333333",
                  "frameTimestamp": "2026-08-06T20:00:00Z",
                  "modelVersion": "welding-ppe-v1",
                  "inferenceMs": 40,
                  "detections": []
                }
                """;

        mockMvc.perform(
                        post("/internal/v1/detections")
                                .header(
                                        INTERNAL_API_KEY_HEADER,
                                        INTERNAL_API_KEY
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(
                detectionService,
                never()
        ).process(
                any()
        );
    }

    @Test
    void confidenceGreaterThanOneReturns400() throws Exception {
        String requestBody = """
                {
                  "eventId": "11111111-1111-1111-1111-111111111111",
                  "cameraId": "22222222-2222-2222-2222-222222222222",
                  "sessionId": "33333333-3333-3333-3333-333333333333",
                  "frameTimestamp": "2026-08-06T20:00:00Z",
                  "modelVersion": "welding-ppe-v1",
                  "inferenceMs": 40,
                  "detections": [
                    {
                      "label": "welding_mask",
                      "confidence": 1.50,
                      "bbox": {
                        "x": 0.10,
                        "y": 0.10,
                        "width": 0.20,
                        "height": 0.20
                      }
                    }
                  ]
                }
                """;

        mockMvc.perform(
                        post("/internal/v1/detections")
                                .header(
                                        INTERNAL_API_KEY_HEADER,
                                        INTERNAL_API_KEY
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        requestBody
                                )
                )
                .andExpect(
                        status().isBadRequest()
                );

        verify(
                detectionService,
                never()
        ).process(
                any()
        );
    }
}