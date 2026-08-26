package com.isg.backend.violation.domain;

import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionLabel;

import java.util.List;

public record PersonContext(
        String personKey,
        DetectedObject person,
        List<DetectedObject> associatedDetections
) {

    public PersonContext {
        if (personKey == null || personKey.isBlank()) {
            throw new IllegalArgumentException("personKey must not be blank");
        }

        if (person == null) {
            throw new IllegalArgumentException("person must not be null");
        }

        associatedDetections = associatedDetections == null
                ? List.of()
                : List.copyOf(associatedDetections);
    }

    public boolean hasDetection(DetectionLabel label) {
        return associatedDetections.stream()
                .anyMatch(detection -> detection.label() == label);
    }
}