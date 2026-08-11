package com.isg.backend.violation.domain;

import com.isg.backend.violation.domain.detection.BoundingBox;

import java.time.Instant;
import java.util.UUID;

public record CandidateViolation(
        UUID eventId,
        UUID cameraId,
        UUID sessionId,
        String personKey,
        ViolationType violationType,
        BoundingBox personBox,
        Instant frameTimestamp,
        double confidence
) {
}