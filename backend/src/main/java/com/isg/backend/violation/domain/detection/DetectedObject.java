package com.isg.backend.violation.domain.detection;

public record DetectedObject(
        DetectionLabel label,
        String rawLabel,
        double confidence,
        BoundingBox boundingBox,
        String trackId
) {
}