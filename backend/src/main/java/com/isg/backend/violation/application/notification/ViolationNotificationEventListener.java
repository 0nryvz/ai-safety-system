package com.isg.backend.violation.application.notification;

import com.isg.backend.violation.application.event.ViolationRecordingUpdatedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.DepartmentNameResolver;
import com.isg.backend.violation.application.port.NotificationRecipientResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@ConditionalOnBean({
        NotificationRecipientResolver.class,
        DepartmentNameResolver.class
})
public class ViolationNotificationEventListener {

    private final ViolationNotificationService notificationService;

    public ViolationNotificationEventListener(
            ViolationNotificationService notificationService
    ) {
        this.notificationService =
                notificationService;
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onViolationStarted(
            ViolationStartedEvent event
    ) {
        notificationService.sendViolationStarted(
                event
        );
    }

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    public void onViolationRecordingUpdated(
            ViolationRecordingUpdatedEvent event
    ) {
        notificationService.sendViolationUpdate(
                event
        );
    }
}