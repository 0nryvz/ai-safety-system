package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
import com.isg.backend.violation.domain.temporal.EndedViolation;
import com.isg.backend.violation.domain.temporal.TemporalViolationTransitions;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import com.isg.backend.violation.dto.BoundingBox;
import com.isg.backend.violation.dto.DetectionItem;
import com.isg.backend.violation.dto.DetectionRequest;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import com.isg.backend.violation.mapper.DetectionMapper;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetectionServiceTest {

    private CameraQueryService cameraQueryService;
    private DetectionMapper detectionMapper;
    private DuplicateEventGuard duplicateEventGuard;
    private CandidateViolationEvaluator candidateViolationEvaluator;
    private TemporalConfirmationService temporalConfirmationService;
    private ViolationLifecycleService violationLifecycleService;
    private ActiveViolationRegistry activeViolationRegistry;
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

        violationLifecycleService =
                mock(ViolationLifecycleService.class);

        activeViolationRegistry =
                new ActiveViolationRegistry();

        detectionService =
                new DetectionService(
                        cameraQueryService,
                        detectionMapper,
                        duplicateEventGuard,
                        candidateViolationEvaluator,
                        temporalConfirmationService,
                        violationLifecycleService,
                        activeViolationRegistry
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

        prepareValidPipeline(
                request,
                frame,
                List.of(candidate)
        );

        when(temporalConfirmationService.processFrameTransitions(
                frame.frameTimestamp(),
                List.of(candidate)
        )).thenReturn(
                new TemporalViolationTransitions(
                        List.of(),
                        List.of()
                )
        );

        detectionService.process(
                request
        );

        verify(cameraQueryService)
                .isValid(
                        request.cameraId(),
                        request.sessionId()
                );

        verify(detectionMapper)
                .toDomain(
                        request
                );

        verify(candidateViolationEvaluator)
                .evaluate(
                        frame
                );

        verify(temporalConfirmationService)
                .processFrameTransitions(
                        frame.frameTimestamp(),
                        List.of(candidate)
                );

        verify(
                violationLifecycleService,
                never()
        ).startViolation(
                any(),
                any()
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

        prepareValidPipeline(
                request,
                frame,
                List.of()
        );

        when(temporalConfirmationService.processFrameTransitions(
                frame.frameTimestamp(),
                List.of()
        )).thenReturn(
                new TemporalViolationTransitions(
                        List.of(),
                        List.of()
                )
        );

        detectionService.process(
                request
        );

        verify(temporalConfirmationService)
                .processFrameTransitions(
                        frame.frameTimestamp(),
                        List.of()
                );
    }

    @Test
    void persistsStartedViolationAndRegistersActiveMapping() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        DetectionFrame frame =
                domainFrame(request);

        CandidateViolation candidate =
                candidate(frame);

        ConfirmedViolation confirmation =
                confirmed(
                        candidate
                );

        UUID violationId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(
                        ViolationJpaEntity.class
                );

        when(violation.getId())
                .thenReturn(
                        violationId
                );

        prepareValidPipeline(
                request,
                frame,
                List.of(candidate)
        );

        when(temporalConfirmationService.processFrameTransitions(
                frame.frameTimestamp(),
                List.of(candidate)
        )).thenReturn(
                new TemporalViolationTransitions(
                        List.of(confirmation),
                        List.of()
                )
        );

        when(violationLifecycleService.startViolation(
                confirmation,
                frame.modelVersion()
        )).thenReturn(
                violation
        );

        detectionService.process(
                request
        );

        verify(violationLifecycleService)
                .startViolation(
                        confirmation,
                        frame.modelVersion()
                );

        assertThat(
                activeViolationRegistry.find(
                        confirmation.stateKey()
                )
        ).contains(
                violationId
        );
    }

    @Test
    void doesNotPersistSecondViolationForAlreadyActiveState() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        DetectionRequest firstRequest =
                validRequest(
                        UUID.randomUUID(),
                        cameraId,
                        sessionId,
                        Instant.now()
                );

        DetectionRequest secondRequest =
                validRequest(
                        UUID.randomUUID(),
                        cameraId,
                        sessionId,
                        firstRequest.frameTimestamp()
                                .plusMillis(100)
                );

        DetectionFrame firstFrame =
                domainFrame(
                        firstRequest
                );

        DetectionFrame secondFrame =
                domainFrame(
                        secondRequest
                );

        CandidateViolation firstCandidate =
                candidate(
                        firstFrame
                );

        CandidateViolation secondCandidate =
                candidate(
                        secondFrame
                );

        ConfirmedViolation confirmation =
                confirmed(
                        firstCandidate
                );

        UUID violationId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(
                        ViolationJpaEntity.class
                );

        when(violation.getId())
                .thenReturn(
                        violationId
                );

        prepareValidPipeline(
                firstRequest,
                firstFrame,
                List.of(firstCandidate)
        );

        prepareValidPipeline(
                secondRequest,
                secondFrame,
                List.of(secondCandidate)
        );

        when(temporalConfirmationService.processFrameTransitions(
                firstFrame.frameTimestamp(),
                List.of(firstCandidate)
        )).thenReturn(
                new TemporalViolationTransitions(
                        List.of(confirmation),
                        List.of()
                )
        );

        when(temporalConfirmationService.processFrameTransitions(
                secondFrame.frameTimestamp(),
                List.of(secondCandidate)
        )).thenReturn(
                new TemporalViolationTransitions(
                        List.of(confirmation),
                        List.of()
                )
        );

        when(violationLifecycleService.startViolation(
                confirmation,
                firstFrame.modelVersion()
        )).thenReturn(
                violation
        );

        detectionService.process(
                firstRequest
        );

        detectionService.process(
                secondRequest
        );

        verify(
                violationLifecycleService,
                times(1)
        ).startViolation(
                confirmation,
                firstFrame.modelVersion()
        );

        assertThat(
                activeViolationRegistry.find(
                        confirmation.stateKey()
                )
        ).contains(
                violationId
        );
    }

    @Test
    void endsMappedViolationWhenTemporalEngineReportsEnd() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        DetectionFrame frame =
                domainFrame(
                        request
                );

        CandidateViolation candidate =
                candidate(
                        frame
                );

        ViolationStateKey stateKey =
                ViolationStateKey.from(
                        candidate
                );

        UUID violationId =
                UUID.randomUUID();

        activeViolationRegistry.getOrCreate(
                stateKey,
                () -> violationId
        );

        Instant endedAt =
                frame.frameTimestamp()
                        .minusMillis(100);

        EndedViolation endedViolation =
                new EndedViolation(
                        stateKey,
                        candidate.cameraId(),
                        candidate.sessionId(),
                        candidate.violationType(),
                        endedAt
                );

        prepareValidPipeline(
                request,
                frame,
                List.of()
        );

        when(temporalConfirmationService.processFrameTransitions(
                frame.frameTimestamp(),
                List.of()
        )).thenReturn(
                new TemporalViolationTransitions(
                        List.of(),
                        List.of(endedViolation)
                )
        );

        detectionService.process(
                request
        );

        verify(violationLifecycleService)
                .endViolation(
                        violationId,
                        endedAt
                );

        assertThat(
                activeViolationRegistry.find(
                        stateKey
                )
        ).isEmpty();
    }

    @Test
    void doesNotEndDatabaseViolationWhenActiveMappingIsMissing() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        DetectionFrame frame =
                domainFrame(
                        request
                );

        CandidateViolation candidate =
                candidate(
                        frame
                );

        ViolationStateKey stateKey =
                ViolationStateKey.from(
                        candidate
                );

        EndedViolation endedViolation =
                new EndedViolation(
                        stateKey,
                        candidate.cameraId(),
                        candidate.sessionId(),
                        candidate.violationType(),
                        frame.frameTimestamp()
                );

        prepareValidPipeline(
                request,
                frame,
                List.of()
        );

        when(temporalConfirmationService.processFrameTransitions(
                frame.frameTimestamp(),
                List.of()
        )).thenReturn(
                new TemporalViolationTransitions(
                        List.of(),
                        List.of(endedViolation)
                )
        );

        detectionService.process(
                request
        );

        verify(
                violationLifecycleService,
                never()
        ).endViolation(
                any(),
                any()
        );
    }

    @Test
    void keepsActiveMappingWhenEndingViolationFails() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        DetectionFrame frame =
                domainFrame(
                        request
                );

        CandidateViolation candidate =
                candidate(
                        frame
                );

        ViolationStateKey stateKey =
                ViolationStateKey.from(
                        candidate
                );

        UUID violationId =
                UUID.randomUUID();

        activeViolationRegistry.getOrCreate(
                stateKey,
                () -> violationId
        );

        Instant endedAt =
                frame.frameTimestamp();

        EndedViolation endedViolation =
                new EndedViolation(
                        stateKey,
                        candidate.cameraId(),
                        candidate.sessionId(),
                        candidate.violationType(),
                        endedAt
                );

        prepareValidPipeline(
                request,
                frame,
                List.of()
        );

        when(temporalConfirmationService.processFrameTransitions(
                frame.frameTimestamp(),
                List.of()
        )).thenReturn(
                new TemporalViolationTransitions(
                        List.of(),
                        List.of(endedViolation)
                )
        );

        doThrow(
                new RuntimeException(
                        "database failure"
                )
        ).when(
                violationLifecycleService
        ).endViolation(
                violationId,
                endedAt
        );

        assertThatThrownBy(
                () -> detectionService.process(
                        request
                )
        )
                .isInstanceOf(
                        RuntimeException.class
                )
                .hasMessage(
                        "database failure"
                );

        assertThat(
                activeViolationRegistry.find(
                        stateKey
                )
        ).contains(
                violationId
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

        verify(
                detectionMapper,
                never()
        ).toDomain(
                request
        );
    }

    @Test
    void duplicateEventReturns409BeforeCandidateEvaluation() {
        DetectionRequest request =
                validRequest(
                        Instant.now()
                );

        DetectionFrame frame =
                domainFrame(
                        request
                );

        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(true);

        when(detectionMapper.toDomain(
                request
        )).thenReturn(
                frame
        );

        when(duplicateEventGuard.isFirstOccurrence(
                request.eventId()
        )).thenReturn(false);

        assertStatus(
                () -> detectionService.process(
                        request
                ),
                409
        );

        verify(
                candidateViolationEvaluator,
                never()
        ).evaluate(
                any()
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

        verify(
                cameraQueryService,
                never()
        ).isValid(
                request.cameraId(),
                request.sessionId()
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

        verify(
                cameraQueryService,
                never()
        ).isValid(
                request.cameraId(),
                request.sessionId()
        );
    }

    private void prepareValidPipeline(
            DetectionRequest request,
            DetectionFrame frame,
            List<CandidateViolation> candidates
    ) {
        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(true);

        when(detectionMapper.toDomain(
                request
        )).thenReturn(
                frame
        );

        when(duplicateEventGuard.isFirstOccurrence(
                request.eventId()
        )).thenReturn(true);

        when(candidateViolationEvaluator.evaluate(
                frame
        )).thenReturn(
                candidates
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
        return validRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                timestamp
        );
    }

    private DetectionRequest validRequest(
            UUID eventId,
            UUID cameraId,
            UUID sessionId,
            Instant timestamp
    ) {
        return new DetectionRequest(
                eventId,
                cameraId,
                sessionId,
                timestamp,
                "welding-ppe-v1",
                40L,
                List.of(
                        new DetectionItem(
                                "welding_mask",
                                new BigDecimal(
                                        "0.90"
                                ),
                                new BoundingBox(
                                        new BigDecimal("0.10"),
                                        new BigDecimal("0.10"),
                                        new BigDecimal("0.20"),
                                        new BigDecimal("0.20")
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
                ViolationStateKey.from(
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