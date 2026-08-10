package com.isg.backend.recording.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Recording {

    private final UUID id;
    private final UUID violationId;
    private RecordingStatus status;
    private Instant recordingStartedAt;

    private Recording(
            UUID id,
            UUID violationId,
            RecordingStatus status,
            Instant recordingStartedAt
    ) {
        this.id = id;
        this.violationId = Objects.requireNonNull(
                violationId,
                "violationId cannot be null"
        );
        this.status = Objects.requireNonNull(
                status,
                "status cannot be null"
        );
        this.recordingStartedAt = recordingStartedAt;
    }

    public static Recording createRequested(
            UUID violationId
    ) {
        return new Recording(
                null,
                violationId,
                RecordingStatus.REQUESTED,
                null
        );
    }

    public static Recording rehydrate(
            UUID id,
            UUID violationId,
            RecordingStatus status,
            Instant recordingStartedAt
    ) {
        return new Recording(
                id,
                violationId,
                status,
                recordingStartedAt
        );
    }

    public void markRecordingStarted(
            Instant startedAt
    ) {
        this.recordingStartedAt = Objects.requireNonNull(
                startedAt,
                "startedAt cannot be null"
        );

        if (status == RecordingStatus.REQUESTED) {
            this.status = RecordingStatus.RECORDING;
        }
    }

    public void markProcessing() {
        if (status == RecordingStatus.RECORDING || status == RecordingStatus.REQUESTED) {
            this.status = RecordingStatus.PROCESSING;
        }
    }

    public boolean stopAlreadyHandled() {
        return status == RecordingStatus.PROCESSING
                || status == RecordingStatus.READY
                || status == RecordingStatus.ERROR;
    }

    public UUID id() {
        return id;
    }

    public UUID violationId() {
        return violationId;
    }

    public RecordingStatus status() {
        return status;
    }

    public Instant recordingStartedAt() {
        return recordingStartedAt;
    }
}
