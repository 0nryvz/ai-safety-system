package com.isg.backend.violation.application.port;

import java.util.Objects;

public record RecordingQueryResult(
        String recordingStatus,
        boolean clipReady,
        String playbackUrl
) {

    public RecordingQueryResult {
        if (recordingStatus == null
                || recordingStatus.isBlank()) {
            throw new IllegalArgumentException(
                    "recordingStatus must not be blank"
            );
        }

        if (!clipReady
                && playbackUrl != null) {
            throw new IllegalArgumentException(
                    "playbackUrl must be null when clip is not ready"
            );
        }

        if (playbackUrl != null
                && playbackUrl.isBlank()) {
            throw new IllegalArgumentException(
                    "playbackUrl must not be blank"
            );
        }
    }

    public static RecordingQueryResult notReady(
            String recordingStatus
    ) {
        Objects.requireNonNull(
                recordingStatus,
                "recordingStatus must not be null"
        );

        return new RecordingQueryResult(
                recordingStatus,
                false,
                null
        );
    }

    public static RecordingQueryResult ready(
            String playbackUrl
    ) {
        return new RecordingQueryResult(
                "READY",
                true,
                playbackUrl
        );
    }
}