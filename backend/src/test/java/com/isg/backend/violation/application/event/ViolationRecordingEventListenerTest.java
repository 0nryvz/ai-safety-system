package com.isg.backend.violation.application.event;

import com.isg.backend.violation.application.port.RecordingCommandPort;
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

class ViolationRecordingEventListenerTest {

    @Test
    void forwardsStartedEventToRecordingCommandPort() {
        RecordingCommandPort recordingCommandPort =
                mock(RecordingCommandPort.class);

        ViolationRecordingEventListener listener =
                new ViolationRecordingEventListener(
                        recordingCommandPort
                );

        ViolationStartedEvent event =
                new ViolationStartedEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ViolationType.MISSING_WELDING_MASK,
                        Instant.parse(
                                "2026-08-10T20:00:00Z"
                        )
                );

        listener.onViolationStarted(
                event
        );

        verify(recordingCommandPort)
                .startRecording(
                        event
                );
    }

    @Test
    void consumesStartedEventOnlyAfterTransactionCommit()
            throws NoSuchMethodException {

        Method listenerMethod =
                ViolationRecordingEventListener.class
                        .getMethod(
                                "onViolationStarted",
                                ViolationStartedEvent.class
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
}