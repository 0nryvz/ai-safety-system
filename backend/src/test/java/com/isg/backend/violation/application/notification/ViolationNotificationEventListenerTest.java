package com.isg.backend.violation.application.notification;

import com.isg.backend.violation.application.event.ViolationRecordingUpdatedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.domain.ViolationType;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ViolationNotificationEventListenerTest {

    @Test
    void forwardsStartedEventToNotificationService() {

        ViolationNotificationService notificationService =
                mock(ViolationNotificationService.class);

        ViolationNotificationEventListener listener =
                new ViolationNotificationEventListener(
                        notificationService
                );

        ViolationStartedEvent event =
                startedEvent();

        listener.onViolationStarted(
                event
        );

        verify(notificationService)
                .sendViolationStarted(
                        event
                );
    }


    @Test
    void forwardsRecordingUpdateEventToNotificationService() {

        ViolationNotificationService notificationService =
                mock(ViolationNotificationService.class);

        ViolationNotificationEventListener listener =
                new ViolationNotificationEventListener(
                        notificationService
                );

        ViolationRecordingUpdatedEvent event =
                recordingUpdatedEvent();

        listener.onViolationRecordingUpdated(
                event
        );

        verify(notificationService)
                .sendViolationUpdate(
                        event
                );
    }


    @Test
    void consumesStartedEventOnlyAfterTransactionCommit()
            throws NoSuchMethodException {

        assertAfterCommit(
                "onViolationStarted",
                ViolationStartedEvent.class
        );
    }


    @Test
    void consumesRecordingUpdateOnlyAfterTransactionCommit()
            throws NoSuchMethodException {

        assertAfterCommit(
                "onViolationRecordingUpdated",
                ViolationRecordingUpdatedEvent.class
        );
    }


    private void assertAfterCommit(
            String methodName,
            Class<?> eventType
    ) throws NoSuchMethodException {

        Method method =
                ViolationNotificationEventListener.class
                        .getMethod(
                                methodName,
                                eventType
                        );

        TransactionalEventListener annotation =
                method.getAnnotation(
                        TransactionalEventListener.class
                );

        assertThat(annotation)
                .isNotNull();

        assertThat(annotation.phase())
                .isEqualTo(
                        TransactionPhase.AFTER_COMMIT
                );
    }


    private ViolationStartedEvent startedEvent() {

        Instant now =
                Instant.parse(
                        "2026-08-10T20:00:00Z"
                );

        return new ViolationStartedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ViolationType.MISSING_WELDING_MASK,
                now
        );
    }


    private ViolationRecordingUpdatedEvent recordingUpdatedEvent() {

        return new ViolationRecordingUpdatedEvent(
                UUID.randomUUID(),
                "READY",
                "clips/test.mp4",
                true,
                Instant.parse(
                        "2026-08-10T20:00:10Z"
                ),
                "cover/test.jpg"
        );
    }
}