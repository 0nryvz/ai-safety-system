package com.isg.backend.recording.application.port;

import com.isg.backend.recording.application.StartRecordingCommand;
import com.isg.backend.recording.application.StopRecordingCommand;

import java.util.UUID;

public interface GatewayRecordingCommandPort {

    void sendStart(
            UUID recordingId,
            StartRecordingCommand command
    );

    void sendStop(
            StopRecordingCommand command
    );
}
