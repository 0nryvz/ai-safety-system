package com.isg.backend.violation.domain.detection;

import com.isg.backend.violation.exception.UnsupportedDetectionLabelException;

import java.util.Arrays;

public enum DetectionLabel {

    PERSON("person"),
    WELDING("welding"),
    WELDING_MASK("welding_mask"),
    WELDING_APRON("welding_apron"),
    GLOVES("gloves"),
    WELDING_JACKET("welding_jacket");

    private final String[] rawValues;

    DetectionLabel(String... rawValues) {
        this.rawValues = rawValues;
    }

    public static DetectionLabel fromRawValue(
            String rawValue
    ) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new UnsupportedDetectionLabelException(
                    rawValue
            );
        }

        return Arrays.stream(values())
                .filter(label ->
                        Arrays.stream(label.rawValues)
                                .anyMatch(value ->
                                        value.equalsIgnoreCase(
                                                rawValue.trim()
                                        )
                                )
                )
                .findFirst()
                .orElseThrow(() ->
                        new UnsupportedDetectionLabelException(
                                rawValue
                        )
                );
    }
}