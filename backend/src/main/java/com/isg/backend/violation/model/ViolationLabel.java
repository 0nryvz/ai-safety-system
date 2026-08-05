package com.isg.backend.violation.model;

public enum ViolationLabel {

    WELDER("welder"),
    NON_WELDER("non_welder"),

    WELDING("welding"),

    WELDING_MASK("welding_mask"),
    WELDING_APRON("welding_apron"),
    WELDING_JACKET("welding_jacket"),
    GLOVES("gloves"),

    NON_MASK("non_mask"),
    NON_GLOVES("non_gloves");

    private final String value;

    ViolationLabel(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static ViolationLabel fromValue(String value) {
        for (ViolationLabel label : values()) {
            if (label.value.equalsIgnoreCase(value)) {
                return label;
            }
        }
        throw new IllegalArgumentException("Unknown label: " + value);
    }
}