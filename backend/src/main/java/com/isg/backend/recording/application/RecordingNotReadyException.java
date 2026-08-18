package com.isg.backend.recording.application;

import com.isg.backend.recording.domain.RecordingStatus;

import java.util.UUID;

public class RecordingNotReadyException
        extends RuntimeException {

    public RecordingNotReadyException(
            UUID violationId,
            RecordingStatus status
    ) {
        super(
                "Recording is not ready for violationId="
                        + violationId
                        + ", status="
                        + status
        );
    }
}