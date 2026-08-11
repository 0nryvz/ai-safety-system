package com.isg.backend.recording.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Recording {

    private final UUID id;
    private final UUID violationId;
    private RecordingStatus status;
    private Instant recordingStartedAt;
    private UUID startCommandId;
    private UUID stopCommandId;

    private Recording(
            UUID id,
            UUID violationId,
            RecordingStatus status,
            Instant recordingStartedAt,
            UUID startCommandId,
            UUID stopCommandId
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
        this.startCommandId = startCommandId;
        this.stopCommandId = stopCommandId;
    }

    public static Recording createRequested(
            UUID violationId,
            UUID startCommandId
    ) {
        return new Recording(
                null,
                violationId,
                RecordingStatus.REQUESTED,
                null,
                Objects.requireNonNull(startCommandId, "startCommandId cannot be null"),
                null
        );
    }

    public static Recording rehydrate(
            UUID id,
            UUID violationId,
            RecordingStatus status,
            Instant recordingStartedAt,
            UUID startCommandId,
            UUID stopCommandId
    ) {
        return new Recording(
                id,
                violationId,
                status,
                recordingStartedAt,
                startCommandId,
                stopCommandId
        );
    }

    public void markRecordingStarted(
            Instant startedAt,
            UUID startCommandId
    ) {
        this.recordingStartedAt = Objects.requireNonNull(
                startedAt,
                "startedAt cannot be null"
        );
        this.startCommandId = Objects.requireNonNull(
                startCommandId,
                "startCommandId cannot be null"
        );

        if (status == RecordingStatus.REQUESTED) {
            this.status = RecordingStatus.RECORDING;
        }
    }

    public void markProcessing(
            UUID stopCommandId
    ) {
        this.stopCommandId = Objects.requireNonNull(
                stopCommandId,
                "stopCommandId cannot be null"
        );

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

    public UUID startCommandId() {
        return startCommandId;
    }

    public UUID stopCommandId() {
        return stopCommandId;
    }
}
