package com.isg.backend.recording.application;

import java.util.UUID;

public class RecordingNotFoundForViolationException extends RuntimeException {
    public RecordingNotFoundForViolationException(
            UUID violationId
    ) {
        super("Recording not found for violationId=" + violationId);
    }
}
