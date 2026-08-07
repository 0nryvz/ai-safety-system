package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.dto.DetectionRequest;
import com.isg.backend.violation.mapper.DetectionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;

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

    public DetectionService(
            CameraQueryService cameraQueryService,
            DetectionMapper detectionMapper,
            DuplicateEventGuard duplicateEventGuard
    ) {
        this.cameraQueryService = cameraQueryService;
        this.detectionMapper = detectionMapper;
        this.duplicateEventGuard = duplicateEventGuard;
    }

    public void process(DetectionRequest request) {

        validateTimestamp(request.frameTimestamp());

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
         * Önce DTO domain modeline dönüştürülür.
         * Bilinmeyen label veya geçersiz domain bbox varsa event,
         * duplicate belleğine kaydedilmez.
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

        logger.info(
                "Detection accepted. eventId={}, cameraId={}, detectionCount={}",
                request.eventId(),
                request.cameraId(),
                frame.detections().size()
        );

        /*
         * TODO ADIM 2:
         * DetectionFrame, ViolationRuleEngine'e gönderilecek.
         */
    }

    private void validateTimestamp(
            Instant frameTimestamp
    ) {
        Instant now = Instant.now();

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