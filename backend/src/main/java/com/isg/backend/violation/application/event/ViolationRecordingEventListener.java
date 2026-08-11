package com.isg.backend.violation.application.event;

import com.isg.backend.violation.application.port.RecordingCommandPort;
import com.isg.backend.violation.service.RecordingEventDeliveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnBean(RecordingCommandPort.class)
public class ViolationRecordingEventListener {

    private final RecordingEventDeliveryService deliveryService;

    public ViolationRecordingEventListener(
            RecordingEventDeliveryService deliveryService
    ) {
        this.deliveryService =
                deliveryService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onViolationStarted(
            ViolationStartedEvent event
    ) {
        deliveryService.deliverStart(
                event
        );
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onViolationEnded(
            ViolationEndedEvent event
    ) {
        deliveryService.deliverStop(
                event
        );
    }
}