package com.isg.backend.violation.application.event;

import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.service.RecordingEventDeliveryService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ViolationRecordingEventListenerTest {

    @Test
    void forwardsStartedEventToDeliveryService() {
        RecordingEventDeliveryService deliveryService =
                mock(RecordingEventDeliveryService.class);

        ViolationRecordingEventListener listener =
                new ViolationRecordingEventListener(
                        deliveryService
                );

        ViolationStartedEvent event =
                startedEvent();

        listener.onViolationStarted(
                event
        );

        verify(deliveryService)
                .deliverStart(
                        event
                );
    }

    @Test
    void forwardsEndedEventToDeliveryService() {
        RecordingEventDeliveryService deliveryService =
                mock(RecordingEventDeliveryService.class);

        ViolationRecordingEventListener listener =
                new ViolationRecordingEventListener(
                        deliveryService
                );

        ViolationEndedEvent event =
                endedEvent();

        listener.onViolationEnded(
                event
        );

        verify(deliveryService)
                .deliverStop(
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
    void consumesEndedEventOnlyAfterTransactionCommit()
            throws NoSuchMethodException {

        assertAfterCommit(
                "onViolationEnded",
                ViolationEndedEvent.class
        );
    }

    private void assertAfterCommit(
            String methodName,
            Class<?> eventType
    ) throws NoSuchMethodException {

        Method listenerMethod =
                ViolationRecordingEventListener.class
                        .getMethod(
                                methodName,
                                eventType
                        );

        TransactionalEventListener annotation =
                listenerMethod.getAnnotation(
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
        return new ViolationStartedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                ViolationType.MISSING_WELDING_MASK,
                Instant.parse(
                        "2026-08-10T20:00:00Z"
                )
        );
    }

    private ViolationEndedEvent endedEvent() {
        return new ViolationEndedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse(
                        "2026-08-10T20:00:05Z"
                )
        );
    }
}