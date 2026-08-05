package com.isg.backend.violation.service;

import com.isg.backend.violation.dto.DetectionRequest;
import com.isg.backend.camera.service.CameraQueryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DetectionService {

    private static final Logger logger = LoggerFactory.getLogger(DetectionService.class);

    private static final Duration MAX_FUTURE_SKEW = Duration.ofSeconds(5);
    private static final Duration MAX_EVENT_AGE = Duration.ofMinutes(2);

    private final Set<UUID> processedEvents = ConcurrentHashMap.newKeySet();
    private final CameraQueryService cameraQueryService;

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

        if (!processedEvents.add(request.eventId())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Duplicate detection event."
            );
        }

        logger.info(
                "Detection accepted. eventId={}, cameraId={}, detectionCount={}",
                request.eventId(),
                request.cameraId(),
                request.detections().size()
        );

        /*
         * Kamera/oturum doğrulama işlemi CameraQueryService'e devredilmiştir.
         * Mevcut uygulama, BE-2 servisi ile değiştirilecektir.
         */

        /*
         * TODO (BE-3)
         * AI label'ları iş kurallarına dönüştürülecek.
         * CandidateViolation üretilecek.
         */

        /*
         * TODO (BE-3)
         * CandidateViolation zaman bazlı doğrulama motoruna gönderilecek.
         */
    }
    public DetectionService(CameraQueryService cameraQueryService) {
        this.cameraQueryService = cameraQueryService;
    }

    private void validateTimestamp(Instant frameTimestamp) {

        Instant now = Instant.now();

        if (frameTimestamp.isAfter(now.plus(MAX_FUTURE_SKEW))) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "frameTimestamp is in the future."
            );
        }

        if (frameTimestamp.isBefore(now.minus(MAX_EVENT_AGE))) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "frameTimestamp is too old."
            );
        }
    }
}