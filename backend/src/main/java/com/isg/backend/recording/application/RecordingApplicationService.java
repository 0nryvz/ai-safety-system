package com.isg.backend.recording.application;

import com.isg.backend.recording.application.callback.RecordingStatusCallback;
import com.isg.backend.recording.application.callback.RecordingStatusCallbackPort;
import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecordingApplicationService {

    private final RecordingRepository recordingRepository;
    private final RecordingCreationService recordingCreationService;
    private final GatewayRecordingCommandPort gatewayRecordingCommandPort;
    private final RecordingStatusCallbackPort recordingStatusCallbackPort;
    private final Clock clock;

    @Autowired
    public RecordingApplicationService(
            RecordingRepository recordingRepository,
            RecordingCreationService recordingCreationService,
            GatewayRecordingCommandPort gatewayRecordingCommandPort,
            RecordingStatusCallbackPort recordingStatusCallbackPort
    ) {
        this(
                recordingRepository,
                recordingCreationService,
                gatewayRecordingCommandPort,
                recordingStatusCallbackPort,
                Clock.systemUTC()
        );
    }

    RecordingApplicationService(
            RecordingRepository recordingRepository,
            RecordingCreationService recordingCreationService,
            GatewayRecordingCommandPort gatewayRecordingCommandPort,
            RecordingStatusCallbackPort recordingStatusCallbackPort,
            Clock clock
    ) {
        this.recordingRepository = recordingRepository;
        this.recordingCreationService = recordingCreationService;
        this.gatewayRecordingCommandPort = gatewayRecordingCommandPort;
        this.recordingStatusCallbackPort = recordingStatusCallbackPort;
        this.clock = clock;
    }

    @Transactional
    public Recording start(
            StartRecordingCommand command
    ) {
        Recording recording = recordingRepository.findByViolationId(command.violationId())
                .orElseGet(() -> createRequestedRecording(command));

        if (recording.status() != RecordingStatus.REQUESTED) {
            return recording;
        }

        UUID startCommandId = recording.startCommandId() == null
                ? command.commandId()
                : recording.startCommandId();

        StartRecordingCommand startCommand = withStartCommandId(command, startCommandId);
        gatewayRecordingCommandPort.sendStart(recording.id(), startCommand);
        recording.markRecordingStarted(startCommand.startedAt(), startCommandId);
        return recordingRepository.save(recording);
    }

    @Transactional
    public Recording stop(
            StopRecordingCommand command
    ) {
        Recording recording = recordingRepository.findByViolationId(command.violationId())
                .orElseThrow(() -> new RecordingNotFoundForViolationException(command.violationId()));

        if (recording.stopAlreadyHandled()) {
            return recording;
        }

        if (recording.status() != RecordingStatus.RECORDING) {
            return recording;
        }

        if (recording.stopCommandId() == null) {
            recording.assignStopCommandId(command.commandId());
            recording = recordingRepository.save(recording);
        }

        StopRecordingCommand stopCommand = withStopCommandId(command, recording.stopCommandId());
        gatewayRecordingCommandPort.sendStop(stopCommand);
        recording.markProcessing(stopCommand.commandId());
        return recordingRepository.save(recording);
    }

    @Transactional
    public void handleCallback(
            RecordingCallbackCommand callback
    ) {
        Recording recording = recordingRepository.findById(callback.recordingId())
                .orElseThrow(() -> new RecordingNotFoundException(callback.recordingId()));

        if (!recording.violationId().equals(callback.violationId())) {
            throw new RecordingCallbackConflictException(
                    "Violation mismatch for recordingId=" + callback.recordingId()
            );
        }

        if (callback.status() == RecordingStatus.READY) {
            handleReadyCallback(recording, callback);
            return;
        }

        if (callback.status() == RecordingStatus.ERROR) {
            handleErrorCallback(recording, callback);
            return;
        }

        throw new RecordingCallbackConflictException(
                "Unsupported callback status=" + callback.status()
        );
    }

    private void handleReadyCallback(
            Recording recording,
            RecordingCallbackCommand callback
    ) {
        if (recording.status() == RecordingStatus.READY) {
            if (sameReadyMetadata(recording, callback)) {
                return;
            }

            throw new RecordingCallbackConflictException(
                    "Conflicting READY callback for recordingId=" + recording.id()
            );
        }

        if (recording.status() == RecordingStatus.ERROR) {
            throw new RecordingCallbackConflictException(
                    "Conflicting READY callback for terminal ERROR recordingId=" + recording.id()
            );
        }

        try {
            recording.markReady(
                    callback.objectKey(),
                    requirePositive(callback.durationMs(), "durationMs"),
                    requirePositive(callback.sizeBytes(), "sizeBytes"),
                    Instant.now(clock),
                    callback.checksum()
            );
            recording.updateRetryCount(callback.retryCount());
        } catch (RuntimeException ex) {
            throw new RecordingCallbackConflictException(
                    "READY callback rejected for recordingId=" + recording.id(),
                    ex
            );
        }

        Recording saved = recordingRepository.save(recording);
        recordingStatusCallbackPort.publish(toStatusCallback(saved));
    }

    private void handleErrorCallback(
            Recording recording,
            RecordingCallbackCommand callback
    ) {
        if (recording.status() == RecordingStatus.ERROR) {
            if (sameErrorMetadata(recording, callback)) {
                return;
            }

            throw new RecordingCallbackConflictException(
                    "Conflicting ERROR callback for recordingId=" + recording.id()
            );
        }

        if (recording.status() == RecordingStatus.READY) {
            throw new RecordingCallbackConflictException(
                    "Conflicting ERROR callback for terminal READY recordingId=" + recording.id()
            );
        }

        try {
            recording.markError(callback.errorCode());
            recording.updateRetryCount(callback.retryCount());
        } catch (RuntimeException ex) {
            throw new RecordingCallbackConflictException(
                    "ERROR callback rejected for recordingId=" + recording.id(),
                    ex
            );
        }

        Recording saved = recordingRepository.save(recording);
        recordingStatusCallbackPort.publish(toStatusCallback(saved));
    }

    private boolean sameReadyMetadata(
            Recording recording,
            RecordingCallbackCommand callback
    ) {
        return Objects.equals(recording.objectKey(), callback.objectKey())
                && Objects.equals(recording.durationMs(), callback.durationMs())
                && Objects.equals(recording.sizeBytes(), callback.sizeBytes())
                && Objects.equals(recording.checksum(), callback.checksum())
                && Objects.equals(recording.retryCount(), callback.retryCount());
    }

    private boolean sameErrorMetadata(
            Recording recording,
            RecordingCallbackCommand callback
    ) {
        return Objects.equals(recording.errorCode(), callback.errorCode())
                && Objects.equals(recording.retryCount(), callback.retryCount());
    }

    private int requirePositive(
            Integer value,
            String field
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
        return value;
    }

    private long requirePositive(
            Long value,
            String field
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
        return value;
    }

    private RecordingStatusCallback toStatusCallback(
            Recording recording
    ) {
        return new RecordingStatusCallback(
                recording.id(),
                recording.violationId(),
                recording.status(),
                recording.objectKey(),
                recording.durationMs() == null ? null : recording.durationMs().longValue(),
                recording.sizeBytes(),
                recording.checksum(),
                recording.errorCode()
        );
    }

    private Recording createRequestedRecording(
            StartRecordingCommand command
    ) {
        try {
            return recordingCreationService.createRequested(
                    command.violationId(),
                    command.commandId()
            );
        } catch (DataIntegrityViolationException ex) {
            return recordingRepository.findByViolationId(command.violationId())
                    .orElseThrow(() -> ex);
        }
    }

    private StartRecordingCommand withStartCommandId(
            StartRecordingCommand command,
            UUID commandId
    ) {
        if (command.commandId().equals(commandId)) {
            return command;
        }

        return new StartRecordingCommand(
                commandId,
                command.violationId(),
                command.cameraId(),
                command.sessionId(),
                command.startedAt(),
                command.preBufferSeconds(),
                command.postBufferSeconds(),
                command.maxClipSeconds()
        );
    }

    private StopRecordingCommand withStopCommandId(
            StopRecordingCommand command,
            UUID commandId
    ) {
        if (command.commandId().equals(commandId)) {
            return command;
        }

        return new StopRecordingCommand(
                commandId,
                command.violationId(),
                command.endedAt()
        );
    }
}
