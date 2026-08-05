package com.isg.backend.violation.model;

public enum ViolationLabel {

    PERSON_WITHOUT_HELMET("person_without_helmet");

    private final String value;

    ViolationLabel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ViolationLabel fromValue(String value) {
        for (ViolationLabel label : values()) {
            if (label.value.equals(value)) {
                return label;
            }
        }

        throw new IllegalArgumentException(
                "Unknown violation label: " + value
        );
    }
}