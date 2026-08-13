package com.isg.backend.recording.application.port;

import com.isg.backend.recording.application.StartRecordingCommand;
import com.isg.backend.recording.application.StopRecordingCommand;

public interface GatewayRecordingCommandPort {

    void sendStart(
            StartRecordingCommand command
    );

    void sendStop(
            StopRecordingCommand command
    );
}
