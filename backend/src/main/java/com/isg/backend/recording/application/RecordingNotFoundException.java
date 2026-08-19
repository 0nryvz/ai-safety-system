package com.isg.backend.recording.application;

import java.util.UUID;

public class RecordingNotFoundException extends RuntimeException {
    public RecordingNotFoundException(
            UUID recordingId
    ) {
        super("Recording not found for recordingId=" + recordingId);
    }
}
