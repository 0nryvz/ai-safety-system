package com.isg.backend.recording.application;

import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordingApplicationService {

    private final RecordingRepository recordingRepository;
    private final GatewayRecordingCommandPort gatewayRecordingCommandPort;

    public RecordingApplicationService(
            RecordingRepository recordingRepository,
            GatewayRecordingCommandPort gatewayRecordingCommandPort
    ) {
        this.recordingRepository = recordingRepository;
        this.gatewayRecordingCommandPort = gatewayRecordingCommandPort;
    }

    @Transactional
    public Recording start(
            StartRecordingCommand command
    ) {
        return recordingRepository.findByViolationId(command.violationId())
                .orElseGet(() -> {
                    Recording recording = Recording.createRequested(
                            command.violationId(),
                            command.commandId()
                    );
                    Recording saved = recordingRepository.save(recording);
                    gatewayRecordingCommandPort.sendStart(command);
                    saved.markRecordingStarted(command.startedAt(), command.commandId());
                    return recordingRepository.save(saved);
                });
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

        if (recording.stopCommandId() != null) {
            if (recording.stopCommandId().equals(command.commandId())) {
                return recording;
            }

            return recording;
        }

        if (recording.status() == RecordingStatus.RECORDING) {
            gatewayRecordingCommandPort.sendStop(command);
            recording.markProcessing(command.commandId());
            return recordingRepository.save(recording);
        }

        return recording;
    }
}
