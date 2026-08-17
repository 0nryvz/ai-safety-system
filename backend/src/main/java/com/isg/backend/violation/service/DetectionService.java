package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
import com.isg.backend.violation.domain.temporal.EndedViolation;
import com.isg.backend.violation.domain.temporal.TemporalViolationTransitions;
import com.isg.backend.violation.dto.DetectionRequest;
import com.isg.backend.violation.mapper.DetectionMapper;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class DetectionService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    DetectionService.class
            );

    private static final Duration MAX_FUTURE_SKEW =
            Duration.ofSeconds(5);

    private static final Duration MAX_EVENT_AGE =
            Duration.ofMinutes(2);

    private final CameraQueryService cameraQueryService;
    private final DetectionMapper detectionMapper;
    private final DuplicateEventGuard duplicateEventGuard;
    private final CandidateViolationEvaluator candidateViolationEvaluator;
    private final TemporalConfirmationService temporalConfirmationService;
    private final ViolationLifecycleService violationLifecycleService;
    private final ActiveViolationRegistry activeViolationRegistry;

    public DetectionService(
            CameraQueryService cameraQueryService,
            DetectionMapper detectionMapper,
            DuplicateEventGuard duplicateEventGuard,
            CandidateViolationEvaluator candidateViolationEvaluator,
            TemporalConfirmationService temporalConfirmationService,
            ViolationLifecycleService violationLifecycleService,
            ActiveViolationRegistry activeViolationRegistry
    ) {
        this.cameraQueryService =
                cameraQueryService;

        this.detectionMapper =
                detectionMapper;

        this.duplicateEventGuard =
                duplicateEventGuard;

        this.candidateViolationEvaluator =
                candidateViolationEvaluator;

        this.temporalConfirmationService =
                temporalConfirmationService;

        this.violationLifecycleService =
                violationLifecycleService;

        this.activeViolationRegistry =
                activeViolationRegistry;
    }

    public void process(
            DetectionRequest request
    ) {
        validateTimestamp(
                request.frameTimestamp()
        );

        if (!cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Camera or session not found."
            );
        }

        DetectionFrame frame =
                detectionMapper.toDomain(
                        request
                );

        if (!duplicateEventGuard.isFirstOccurrence(
                request.eventId()
        )) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Duplicate detection event."
            );
        }

        List<CandidateViolation> candidates =
                candidateViolationEvaluator.evaluate(
                        frame
                );

        TemporalViolationTransitions transitions =
                temporalConfirmationService.processFrameTransitions(
                        frame.frameTimestamp(),
                        candidates
                );

        List<ConfirmedViolation> startedViolations =
                transitions.started();

        for (int index = 0;
             index < startedViolations.size();
             index++) {

            ConfirmedViolation confirmation =
                    startedViolations.get(
                            index
                    );

            try {
                activeViolationRegistry.getOrCreate(
                        confirmation.stateKey(),
                        () ->
                                violationLifecycleService
                                        .startViolation(
                                                confirmation,
                                                frame.modelVersion()
                                        )
                                        .getId()
                );
            } catch (RuntimeException exception) {
                for (int rollbackIndex = index;
                     rollbackIndex < startedViolations.size();
                     rollbackIndex++) {

                    temporalConfirmationService.rollbackConfirmation(
                            startedViolations
                                    .get(rollbackIndex)
                                    .stateKey()
                    );
                }

                throw exception;
            }
        }

        for (EndedViolation endedViolation
                : transitions.ended()) {

            Optional<UUID> violationId =
                    activeViolationRegistry.find(
                            endedViolation.stateKey()
                    );

            if (violationId.isEmpty()) {
                logger.warn(
                        "Ended temporal violation has no active DB mapping. stateKey={}",
                        endedViolation.stateKey()
                );

                continue;
            }

            violationLifecycleService.endViolation(
                    violationId.get(),
                    endedViolation.endedAt()
            );

            activeViolationRegistry.remove(
                    endedViolation.stateKey()
            );
        }

        logger.info(
                "Detection accepted. eventId={}, cameraId={}, detectionCount={}, candidateCount={}, confirmationCount={}, endedCount={}",
                request.eventId(),
                request.cameraId(),
                frame.detections().size(),
                candidates.size(),
                transitions.started().size(),
                transitions.ended().size()
        );
    }

    private void validateTimestamp(
            Instant frameTimestamp
    ) {
        Instant now =
                Instant.now();

        if (frameTimestamp.isAfter(
                now.plus(
                        MAX_FUTURE_SKEW
                )
        )) {
            logger.warn(
                    "Detection rejected because frameTimestamp is too far in the future. frameTimestamp={}, allowedFutureSkewSeconds={}",
                    frameTimestamp,
                    MAX_FUTURE_SKEW.toSeconds()
            );

            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "frameTimestamp is in the future."
            );
        }

        if (frameTimestamp.isBefore(
                now.minus(
                        MAX_EVENT_AGE
                )
        )) {
            logger.warn(
                    "Detection rejected because frameTimestamp is too old. frameTimestamp={}, maxEventAgeSeconds={}",
                    frameTimestamp,
                    MAX_EVENT_AGE.toSeconds()
            );

            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "frameTimestamp is too old."
            );
        }
    }
}