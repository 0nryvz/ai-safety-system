package com.isg.backend.violation.service;

import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.RecordingCommandPort;
import com.isg.backend.violation.config.RecordingEventDeliveryProperties;
import com.isg.backend.violation.domain.ViolationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class RecordingEventDeliveryServiceTest {

    private RecordingCommandPort recordingCommandPort;
    private RecordingEventDeliveryProperties properties;
    private RecordingEventDeliveryService deliveryService;

    @BeforeEach
    void setUp() {
        recordingCommandPort =
                mock(RecordingCommandPort.class);

        properties =
                new RecordingEventDeliveryProperties();

        properties.setMaxAttempts(
                3
        );

        deliveryService =
                new RecordingEventDeliveryService(
                        recordingCommandPort,
                        properties
                );
    }

    @Test
    void deliversStartOnlyOnceWhenFirstAttemptSucceeds() {
        ViolationStartedEvent event =
                startedEvent();

        deliveryService.deliverStart(
                event
        );

        verify(
                recordingCommandPort,
                times(1)
        ).startRecording(
                event
        );
    }

    @Test
    void retriesStartWithSameEventUntilDeliverySucceeds() {
        ViolationStartedEvent event =
                startedEvent();

        doThrow(
                new RuntimeException(
                        "temporary recording failure"
                )
        )
                .doNothing()
                .when(
                        recordingCommandPort
                )
                .startRecording(
                        event
                );

        deliveryService.deliverStart(
                event
        );

        verify(
                recordingCommandPort,
                times(2)
        ).startRecording(
                event
        );
    }

    @Test
    void retriesStopWithSameEventUntilDeliverySucceeds() {
        ViolationEndedEvent event =
                endedEvent();

        doThrow(
                new RuntimeException(
                        "temporary recording failure"
                )
        )
                .doNothing()
                .when(
                        recordingCommandPort
                )
                .stopRecording(
                        event
                );

        deliveryService.deliverStop(
                event
        );

        verify(
                recordingCommandPort,
                times(2)
        ).stopRecording(
                event
        );
    }

    @Test
    void stopsRetryingAfterConfiguredMaximumAttempts() {
        ViolationEndedEvent event =
                endedEvent();

        doThrow(
                new RuntimeException(
                        "recording service unavailable"
                )
        )
                .when(
                        recordingCommandPort
                )
                .stopRecording(
                        event
                );

        assertThatCode(
                () -> deliveryService.deliverStop(
                        event
                )
        ).doesNotThrowAnyException();

        verify(
                recordingCommandPort,
                times(3)
        ).stopRecording(
                event
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