package com.isg.backend.violation.domain.detection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DetectionFrame(
        UUID eventId,
        UUID cameraId,
        UUID sessionId,
        Instant frameTimestamp,
        String modelVersion,
        Long inferenceMs,
        List<DetectedObject> detections
) {

    public DetectionFrame {
        detections = detections == null
                ? List.of()
                : List.copyOf(detections);
    }
}