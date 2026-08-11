package com.isg.backend.recording.infrastructure.violation;

import com.isg.backend.recording.application.RecordingApplicationService;
import com.isg.backend.recording.application.StartRecordingCommand;
import com.isg.backend.recording.application.StopRecordingCommand;
import com.isg.backend.recording.config.RecordingCommandProperties;
import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.RecordingCommandPort;
import org.springframework.stereotype.Component;

@Component
public class RecordingCommandBridgeAdapter implements RecordingCommandPort {

    private final RecordingApplicationService recordingApplicationService;
    private final RecordingCommandProperties commandProperties;

    public RecordingCommandBridgeAdapter(
            RecordingApplicationService recordingApplicationService,
            RecordingCommandProperties commandProperties
    ) {
        this.recordingApplicationService = recordingApplicationService;
        this.commandProperties = commandProperties;
    }

    @Override
    public void startRecording(
            ViolationStartedEvent event
    ) {
        recordingApplicationService.start(new StartRecordingCommand(
                event.commandId(),
                event.violationId(),
                event.cameraId(),
                event.sessionId(),
                event.startedAt(),
                commandProperties.getPreBufferSeconds(),
                commandProperties.getPostBufferSeconds(),
                commandProperties.getMaxClipSeconds()
        ));
    }

    @Override
    public void stopRecording(
            ViolationEndedEvent event
    ) {
        recordingApplicationService.stop(new StopRecordingCommand(
                event.commandId(),
                event.violationId(),
                event.endedAt()
        ));
    }
}