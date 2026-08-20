package com.isg.backend.recording.infrastructure.violation;

import com.isg.backend.recording.application.callback.RecordingStatusCallback;
import com.isg.backend.recording.domain.RecordingStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

@Component
public class RecordingStatusCallbackBridgeAdapter
        implements com.isg.backend.recording.application.callback.RecordingStatusCallbackPort {

    private final com.isg.backend.violation.application.port.RecordingStatusCallbackPort callbackPort;
    private final Clock clock;

    @Autowired
    public RecordingStatusCallbackBridgeAdapter(
            com.isg.backend.violation.application.port.RecordingStatusCallbackPort callbackPort
    ) {
        this(
                callbackPort,
                Clock.systemUTC()
        );
    }

    RecordingStatusCallbackBridgeAdapter(
            com.isg.backend.violation.application.port.RecordingStatusCallbackPort callbackPort,
            Clock clock
    ) {
        this.callbackPort = callbackPort;
        this.clock = clock;
    }

    @Override
    public void publish(
            RecordingStatusCallback callback
    ) {
        Instant changedAt = Instant.now(clock);

        if (callback.status() == RecordingStatus.READY) {
            callbackPort.recordingReady(
                    callback.violationId(),
                    changedAt,
                    callback.coverImageKey()
            );
            return;
        }

        if (callback.status() == RecordingStatus.ERROR) {
            callbackPort.recordingError(
                    callback.violationId(),
                    changedAt,
                    callback.errorCode()
            );
        }
    }
}