package com.isg.backend.violation.domain;

import java.time.Instant;
import java.util.UUID;

public record CandidateViolation(

        UUID detectionId,

        UUID personId,

        UUID cameraId,

        UUID sessionId,

        ViolationType violationType,

        Instant frameTimestamp,

        double confidence

) {
}