package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
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

@Service
public class DetectionService {

    private static final Logger logger =
            LoggerFactory.getLogger(DetectionService.class);

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

    public DetectionService(
            CameraQueryService cameraQueryService,
            DetectionMapper detectionMapper,
            DuplicateEventGuard duplicateEventGuard,
            CandidateViolationEvaluator candidateViolationEvaluator,
            TemporalConfirmationService temporalConfirmationService,
            ViolationLifecycleService violationLifecycleService
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

        /*
         * DTO is converted to the domain model before the event is
         * registered in duplicate memory. An unsupported label or
         * invalid domain bounding box therefore does not consume the
         * event id.
         */
        DetectionFrame frame =
                detectionMapper.toDomain(request);

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

        List<ConfirmedViolation> confirmations =
                temporalConfirmationService.processFrame(
                        frame.frameTimestamp(),
                        candidates
                );

        for (ConfirmedViolation confirmation : confirmations) {
            violationLifecycleService.startViolation(
                    confirmation,
                    frame.modelVersion()
            );
        }

        logger.info(
                "Detection accepted. eventId={}, cameraId={}, detectionCount={}, candidateCount={}, confirmationCount={}",
                request.eventId(),
                request.cameraId(),
                frame.detections().size(),
                candidates.size(),
                confirmations.size()
        );
    }

    private void validateTimestamp(
            Instant frameTimestamp
    ) {
        Instant now =
                Instant.now();

        if (frameTimestamp.isAfter(
                now.plus(MAX_FUTURE_SKEW)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "frameTimestamp is in the future."
            );
        }

        if (frameTimestamp.isBefore(
                now.minus(MAX_EVENT_AGE)
        )) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "frameTimestamp is too old."
            );
        }
    }
}