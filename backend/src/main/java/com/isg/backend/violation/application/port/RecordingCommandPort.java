package com.isg.backend.violation.application.port;

import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;

public interface RecordingCommandPort {

    void startRecording(
            ViolationStartedEvent event
    );

    void stopRecording(
            ViolationEndedEvent event
    );
}