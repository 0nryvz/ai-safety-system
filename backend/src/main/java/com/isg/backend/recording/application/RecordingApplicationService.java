package com.isg.backend.recording.application;

import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class RecordingApplicationService {

    private final RecordingRepository recordingRepository;
    private final RecordingCreationService recordingCreationService;
    private final GatewayRecordingCommandPort gatewayRecordingCommandPort;

    public RecordingApplicationService(
            RecordingRepository recordingRepository,
            RecordingCreationService recordingCreationService,
            GatewayRecordingCommandPort gatewayRecordingCommandPort
    ) {
        this.recordingRepository = recordingRepository;
        this.recordingCreationService = recordingCreationService;
        this.gatewayRecordingCommandPort = gatewayRecordingCommandPort;
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
        gatewayRecordingCommandPort.sendStart(startCommand);
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
