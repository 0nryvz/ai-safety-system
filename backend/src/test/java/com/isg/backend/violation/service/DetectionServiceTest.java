package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetectionServiceTest {

    private CameraQueryService cameraQueryService;
    private DetectionMapper detectionMapper;
    private DuplicateEventGuard duplicateEventGuard;
    private CandidateViolationEvaluator candidateViolationEvaluator;
    private TemporalConfirmationService temporalConfirmationService;
    private DetectionService detectionService;

    @BeforeEach
    void setUp() {
        cameraQueryService =
                mock(CameraQueryService.class);

        detectionMapper =
                mock(DetectionMapper.class);

        duplicateEventGuard =
                mock(DuplicateEventGuard.class);

        candidateViolationEvaluator =
                mock(CandidateViolationEvaluator.class);

        temporalConfirmationService =
                mock(TemporalConfirmationService.class);

        detectionService =
                new DetectionService(
                        cameraQueryService,
                        detectionMapper,
                        duplicateEventGuard,
                        candidateViolationEvaluator,
                        temporalConfirmationService
                );
    }

    @Test
    void processesValidDetectionThroughCandidateAndTemporalPipeline() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        DetectionFrame frame =
                domainFrame(request);

        CandidateViolation candidate =
                candidate(frame);

        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(true);

        when(detectionMapper.toDomain(request))
                .thenReturn(frame);

        when(duplicateEventGuard.isFirstOccurrence(
                request.eventId()
        )).thenReturn(true);

        when(candidateViolationEvaluator.evaluate(
                frame
        )).thenReturn(
                List.of(candidate)
        );

        when(temporalConfirmationService.processFrame(
                frame.frameTimestamp(),
                List.of(candidate)
        )).thenReturn(
                List.of()
        );

        detectionService.process(
                request
        );

        verify(cameraQueryService).isValid(
                request.cameraId(),
                request.sessionId()
        );

        verify(detectionMapper)
                .toDomain(request);

        verify(duplicateEventGuard)
                .isFirstOccurrence(
                        request.eventId()
                );

        verify(candidateViolationEvaluator)
                .evaluate(frame);

        verify(temporalConfirmationService)
                .processFrame(
                        frame.frameTimestamp(),
                        List.of(candidate)
                );
    }

    @Test
    void passesEmptyCandidateListToTemporalEngine() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        DetectionFrame frame =
                domainFrame(request);

        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(true);

        when(detectionMapper.toDomain(request))
                .thenReturn(frame);

        when(duplicateEventGuard.isFirstOccurrence(
                request.eventId()
        )).thenReturn(true);

        when(candidateViolationEvaluator.evaluate(
                frame
        )).thenReturn(
                List.of()
        );

        when(temporalConfirmationService.processFrame(
                frame.frameTimestamp(),
                List.of()
        )).thenReturn(
                List.of()
        );

        detectionService.process(
                request
        );

        verify(candidateViolationEvaluator)
                .evaluate(frame);

        verify(temporalConfirmationService)
                .processFrame(
                        frame.frameTimestamp(),
                        List.of()
                );
    }

    @Test
    void acceptsConfirmedTemporalResultWithoutPersistingInStepThree() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        DetectionFrame frame =
                domainFrame(request);

        CandidateViolation candidate =
                candidate(frame);

        ConfirmedViolation confirmation =
                confirmed(candidate);

        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(true);

        when(detectionMapper.toDomain(request))
                .thenReturn(frame);

        when(duplicateEventGuard.isFirstOccurrence(
                request.eventId()
        )).thenReturn(true);

        when(candidateViolationEvaluator.evaluate(
                frame
        )).thenReturn(
                List.of(candidate)
        );

        when(temporalConfirmationService.processFrame(
                frame.frameTimestamp(),
                List.of(candidate)
        )).thenReturn(
                List.of(confirmation)
        );

        detectionService.process(
                request
        );

        verify(temporalConfirmationService)
                .processFrame(
                        frame.frameTimestamp(),
                        List.of(candidate)
                );
    }

    @Test
    void invalidCameraOrSessionReturns404() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(false);

        assertStatus(
                () -> detectionService.process(
                        request
                ),
                404
        );

        verify(detectionMapper, never())
                .toDomain(request);

        verify(candidateViolationEvaluator, never())
                .evaluate(
                        org.mockito.ArgumentMatchers.any()
                );

        verify(
                temporalConfirmationService,
                never()
        ).processFrame(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void duplicateEventReturns409BeforeCandidateEvaluation() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        DetectionFrame frame =
                domainFrame(request);

        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(true);

        when(detectionMapper.toDomain(request))
                .thenReturn(frame);

        when(duplicateEventGuard.isFirstOccurrence(
                request.eventId()
        )).thenReturn(false);

        assertStatus(
                () -> detectionService.process(
                        request
                ),
                409
        );

        verify(candidateViolationEvaluator, never())
                .evaluate(
                        org.mockito.ArgumentMatchers.any()
                );

        verify(
                temporalConfirmationService,
                never()
        ).processFrame(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyList()
        );
    }

    @Test
    void futureTimestampReturns422() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                                .plusSeconds(30)
                );

        assertStatus(
                () -> detectionService.process(
                        request
                ),
                422
        );

        verify(cameraQueryService, never())
                .isValid(
                        request.cameraId(),
                        request.sessionId()
                );

        verify(candidateViolationEvaluator, never())
                .evaluate(
                        org.mockito.ArgumentMatchers.any()
                );
    }

    @Test
    void oldTimestampReturns422() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                                .minusSeconds(180)
                );

        assertStatus(
                () -> detectionService.process(
                        request
                ),
                422
        );

        verify(cameraQueryService, never())
                .isValid(
                        request.cameraId(),
                        request.sessionId()
                );

        verify(candidateViolationEvaluator, never())
                .evaluate(
                        org.mockito.ArgumentMatchers.any()
                );
    }

    private void assertStatus(
            Runnable operation,
            int expectedStatus
    ) {
        assertThatThrownBy(
                operation::run
        )
                .isInstanceOf(
                        ResponseStatusException.class
                )
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;

                    assertThat(
                            statusException
                                    .getStatusCode()
                                    .value()
                    ).isEqualTo(
                            expectedStatus
                    );
                });
    }

    private DetectionRequest validRequest(
            Instant timestamp
    ) {
        return new DetectionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                timestamp,
                "welding-ppe-v1",
                40L,
                List.of(
                        new DetectionItem(
                                "non_mask",
                                new BigDecimal(
                                        "0.90"
                                ),
                                new BoundingBox(
                                        new BigDecimal(
                                                "0.10"
                                        ),
                                        new BigDecimal(
                                                "0.10"
                                        ),
                                        new BigDecimal(
                                                "0.20"
                                        ),
                                        new BigDecimal(
                                                "0.20"
                                        )
                                )
                        )
                )
        );
    }

    private DetectionFrame domainFrame(
            DetectionRequest request
    ) {
        return new DetectionFrame(
                request.eventId(),
                request.cameraId(),
                request.sessionId(),
                request.frameTimestamp(),
                request.modelVersion(),
                request.inferenceMs(),
                List.of()
        );
    }

    private CandidateViolation candidate(
            DetectionFrame frame
    ) {
        return new CandidateViolation(
                frame.eventId(),
                frame.cameraId(),
                frame.sessionId(),
                "track-1",
                ViolationType.MISSING_WELDING_MASK,
                new com.isg.backend.violation.domain.detection.BoundingBox(
                        0.10,
                        0.10,
                        0.30,
                        0.50
                ),
                frame.frameTimestamp(),
                0.90
        );
    }

    private ConfirmedViolation confirmed(
            CandidateViolation candidate
    ) {
        return new ConfirmedViolation(
                com.isg.backend.violation.domain.temporal.ViolationStateKey.from(
                        candidate
                ),
                candidate.cameraId(),
                candidate.sessionId(),
                candidate.violationType(),
                candidate.frameTimestamp()
                        .minusMillis(1500),
                candidate.frameTimestamp(),
                candidate.confidence()
        );
    }
}