package com.isg.backend.violation.domain.detection;

import com.isg.backend.violation.exception.UnsupportedDetectionLabelException;

import java.util.Arrays;

public enum DetectionLabel {

    PERSON("person"),

    WELDING_MASK("welding_mask"),
    WELDING_APRON("welding_apron"),
    WELDING_JACKET("welding_jacket"),
    GLOVES("gloves"),
    WELDING("welding"),

    NON_GLOVES("non_gloves"),
    NON_MASK("non_mask"),
    NON_JACKET("non_jacket");

    private final String[] rawValues;

    DetectionLabel(String... rawValues) {
        this.rawValues = rawValues;
    }

    public static DetectionLabel fromRawValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new UnsupportedDetectionLabelException(rawValue);
        }

        return Arrays.stream(values())
                .filter(label -> Arrays.stream(label.rawValues)
                        .anyMatch(value ->
                                value.equalsIgnoreCase(rawValue.trim())))
                .findFirst()
                .orElseThrow(() ->
                        new UnsupportedDetectionLabelException(rawValue));
    }
}