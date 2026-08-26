package com.isg.backend.recording.infrastructure.gateway;

import com.isg.backend.recording.application.StartRecordingCommand;
import com.isg.backend.recording.application.StopRecordingCommand;
import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@Profile("test")
public class NoOpGatewayRecordingCommandAdapter implements GatewayRecordingCommandPort {

    @Override
    public void sendStart(
            UUID recordingId,
            StartRecordingCommand command
    ) {
    }

    @Override
    public void sendStop(
            StopRecordingCommand command
    ) {
    }
}
