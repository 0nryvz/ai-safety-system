package com.isg.backend.violation.exception;

public class UnsupportedDetectionLabelException extends RuntimeException {

    private final String unsupportedLabel;

    public UnsupportedDetectionLabelException(String unsupportedLabel) {
        super("Unsupported AI detection label: " + unsupportedLabel);
        this.unsupportedLabel = unsupportedLabel;
    }

    public String getUnsupportedLabel() {
        return unsupportedLabel;
    }
}