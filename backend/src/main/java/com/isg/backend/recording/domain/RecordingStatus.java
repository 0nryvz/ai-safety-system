package com.isg.backend.recording.domain;

import java.util.Arrays;

public enum RecordingStatus {
    REQUESTED("PENDING"),
    RECORDING("RECORDING"),
    PROCESSING("PROCESSING"),
    READY("READY"),
    ERROR("ERROR");

    private final String databaseValue;

    RecordingStatus(
            String databaseValue
    ) {
        this.databaseValue = databaseValue;
    }

    public String databaseValue() {
        return databaseValue;
    }

    public static RecordingStatus fromDatabaseValue(
            String value
    ) {
        return Arrays.stream(values())
                .filter(status -> status.databaseValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unsupported recording status: " + value
                ));
    }
}
