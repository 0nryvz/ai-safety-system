package com.isg.backend.recording.application;

public class RecordingCallbackConflictException extends RuntimeException {
    public RecordingCallbackConflictException(
            String message
    ) {
        super(message);
    }

    public RecordingCallbackConflictException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
