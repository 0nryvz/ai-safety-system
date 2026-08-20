package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.domain.temporal.TemporalViolationTransitions;
import com.isg.backend.violation.dto.BoundingBox;
import com.isg.backend.violation.dto.DetectionItem;
import com.isg.backend.violation.dto.DetectionRequest;
import com.isg.backend.violation.mapper.DetectionMapper;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ViolationResilienceAcceptanceTest {

    private DetectionService detectionService;

    private CameraQueryService cameraQueryService;
    private CandidateViolationEvaluator candidateViolationEvaluator;
    private TemporalConfirmationService temporalConfirmationService;
    private ViolationLifecycleService violationLifecycleService;
    private ActiveViolationRegistry activeViolationRegistry;
    private ViolationMetrics violationMetrics;


    @BeforeEach
    void setUp() {

        cameraQueryService =
                mock(CameraQueryService.class);

        candidateViolationEvaluator =
                mock(CandidateViolationEvaluator.class);

        temporalConfirmationService =
                mock(TemporalConfirmationService.class);

        violationLifecycleService =
                mock(ViolationLifecycleService.class);

        activeViolationRegistry =
                new ActiveViolationRegistry();

        violationMetrics =
                mock(ViolationMetrics.class);


        detectionService =
                new DetectionService(
                        cameraQueryService,
                        new DetectionMapper(),
                        new DuplicateEventGuard(),
                        candidateViolationEvaluator,
                        temporalConfirmationService,
                        violationLifecycleService,
                        activeViolationRegistry,
                        violationMetrics,
                        java.time.Clock.systemUTC()
                );
    }


    @Test
    void duplicateDetectionEventDoesNotContinuePipeline() {

        UUID eventId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();


        DetectionRequest request =
                new DetectionRequest(
                        eventId,
                        cameraId,
                        sessionId,
                        Instant.now(),
                        "model-v1",
                        120L,
                        List.of(
                                new DetectionItem(
                                        "person",
                                        BigDecimal.valueOf(0.95),
                                        new BoundingBox(
                                                BigDecimal.ZERO,
                                                BigDecimal.ZERO,
                                                BigDecimal.ONE,
                                                BigDecimal.ONE
                                        )
                                )
                        )
                );


        when(
                cameraQueryService.isValid(
                        cameraId,
                        sessionId
                )
        )
                .thenReturn(true);


        when(
                candidateViolationEvaluator.evaluate(
                        any()
                )
        )
                .thenReturn(
                        List.of()
                );


        when(
                temporalConfirmationService.processFrameTransitions(
                        any(),
                        any()
                )
        )
                .thenReturn(
                        new TemporalViolationTransitions(
                                List.of(),
                                List.of()
                        )
                );


        detectionService.process(
                request
        );


        assertThatThrownBy(
                () ->
                        detectionService.process(
                                request
                        )
        )
                .isInstanceOf(
                        ResponseStatusException.class
                );


        verify(
                candidateViolationEvaluator,
                times(1)
        )
                .evaluate(
                        any()
                );


        verify(
                temporalConfirmationService,
                times(1)
        )
                .processFrameTransitions(
                        any(),
                        any()
                );
    }
}