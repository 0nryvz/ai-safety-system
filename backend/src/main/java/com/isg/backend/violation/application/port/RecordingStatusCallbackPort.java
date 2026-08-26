package com.isg.backend.violation.application.port;

import java.time.Instant;
import java.util.UUID;

public interface RecordingStatusCallbackPort {

    void recordingReady(
            UUID violationId,
            Instant changedAt
    );

    default void recordingReady(
            UUID violationId,
            Instant changedAt,
            String coverImageKey
    ) {
        recordingReady(
                violationId,
                changedAt
        );
    }

    void recordingError(
            UUID violationId,
            Instant changedAt,
            String errorCode
    );
}