package com.isg.backend.violation.application.event;

import com.isg.backend.violation.application.port.RecordingCommandPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnBean(RecordingCommandPort.class)
public class ViolationRecordingEventListener {

    private final RecordingCommandPort recordingCommandPort;

    public ViolationRecordingEventListener(
            RecordingCommandPort recordingCommandPort
    ) {
        this.recordingCommandPort =
                recordingCommandPort;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onViolationStarted(
            ViolationStartedEvent event
    ) {
        recordingCommandPort.startRecording(
                event
        );
    }
}