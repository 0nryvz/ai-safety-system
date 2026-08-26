package com.isg.backend.recording.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Recording {

    private final UUID id;
    private final UUID violationId;
    private RecordingStatus status;
    private String objectKey;
    private Integer durationMs;
    private Long sizeBytes;
    private Integer retryCount;
    private String checksum;
    private String errorCode;
    private Instant recordingStartedAt;
    private UUID startCommandId;
    private UUID stopCommandId;
    private Instant readyAt;

    private Recording(
            UUID id,
            UUID violationId,
            RecordingStatus status,
            String objectKey,
            Integer durationMs,
            Long sizeBytes,
            Integer retryCount,
            String checksum,
            String errorCode,
            Instant recordingStartedAt,
            UUID startCommandId,
            UUID stopCommandId,
            Instant readyAt
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
        this.objectKey = objectKey;
        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.retryCount = retryCount;
        this.checksum = checksum;
        this.errorCode = errorCode;
        this.recordingStartedAt = recordingStartedAt;
        this.startCommandId = startCommandId;
        this.stopCommandId = stopCommandId;
        this.readyAt = readyAt;
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
                null,
                null,
                0,
                null,
                null,
                null,
                Objects.requireNonNull(startCommandId, "startCommandId cannot be null"),
                null,
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
        return rehydrate(
                id,
                violationId,
                status,
                null,
                null,
                null,
                null,
                null,
                null,
                recordingStartedAt,
                startCommandId,
                stopCommandId,
                null
        );
    }

    public static Recording rehydrate(
            UUID id,
            UUID violationId,
            RecordingStatus status,
            String objectKey,
            Integer durationMs,
            Long sizeBytes,
            Integer retryCount,
            String checksum,
            String errorCode,
            Instant recordingStartedAt,
            UUID startCommandId,
            UUID stopCommandId,
            Instant readyAt
    ) {
        return new Recording(
                id,
                violationId,
                status,
                objectKey,
                durationMs,
                sizeBytes,
                retryCount,
                checksum,
                errorCode,
                recordingStartedAt,
                startCommandId,
                stopCommandId,
                readyAt
        );
    }

    public void markRecordingStarted(
            Instant startedAt,
            UUID startCommandId
    ) {
        if (status != RecordingStatus.REQUESTED) {
            throw new IllegalStateException(
                    "Cannot transition to RECORDING from " + status
            );
        }

        this.recordingStartedAt = Objects.requireNonNull(
                startedAt,
                "startedAt cannot be null"
        );
        this.startCommandId = Objects.requireNonNull(
                startCommandId,
                "startCommandId cannot be null"
        );

        this.status = RecordingStatus.RECORDING;
    }

    public void markProcessing(
            UUID stopCommandId
    ) {
        if (status != RecordingStatus.RECORDING) {
            throw new IllegalStateException(
                    "Cannot transition to PROCESSING from " + status
            );
        }

        this.stopCommandId = Objects.requireNonNull(
                stopCommandId,
                "stopCommandId cannot be null"
        );

        this.status = RecordingStatus.PROCESSING;
    }

    public void markReady(
            String objectKey,
            int durationMs,
            long sizeBytes,
            Instant readyAt,
            String checksum
    ) {
        if (status != RecordingStatus.PROCESSING
                && status != RecordingStatus.RECORDING) {
            throw new IllegalStateException(
                    "Cannot transition to READY from " + status
            );
        }

        this.objectKey = requireNonBlank(objectKey, "objectKey");
        if (durationMs <= 0) {
            throw new IllegalArgumentException("durationMs must be > 0");
        }
        if (sizeBytes <= 0) {
            throw new IllegalArgumentException("sizeBytes must be > 0");
        }

        this.durationMs = durationMs;
        this.sizeBytes = sizeBytes;
        this.readyAt = Objects.requireNonNull(readyAt, "readyAt cannot be null");
        this.checksum = checksum;
        this.errorCode = null;
        this.status = RecordingStatus.READY;
    }

    public void markError(
            String errorCode
    ) {
        if (status != RecordingStatus.PROCESSING
                && status != RecordingStatus.RECORDING) {
            throw new IllegalStateException(
                    "Cannot transition to ERROR from " + status
            );
        }

        this.errorCode = requireNonBlank(errorCode, "errorCode");
        this.status = RecordingStatus.ERROR;
    }

    public void updateRetryCount(
            Integer retryCount
    ) {
        if (retryCount == null) {
            return;
        }

        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be >= 0");
        }

        this.retryCount = retryCount;
    }

    private static String requireNonBlank(
            String value,
            String field
    ) {
        Objects.requireNonNull(value, field + " cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " cannot be blank");
        }
        return value;
    }

    public void assignStopCommandId(
            UUID stopCommandId
    ) {
        if (this.stopCommandId == null) {
            this.stopCommandId = Objects.requireNonNull(
                    stopCommandId,
                    "stopCommandId cannot be null"
            );
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

    public String objectKey() {
        return objectKey;
    }

    public Integer durationMs() {
        return durationMs;
    }

    public Long sizeBytes() {
        return sizeBytes;
    }

    public Integer retryCount() {
        return retryCount;
    }

    public String checksum() {
        return checksum;
    }

    public String errorCode() {
        return errorCode;
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

    public Instant readyAt() {
        return readyAt;
    }
}
