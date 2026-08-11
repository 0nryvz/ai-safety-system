package com.isg.backend.violation.service;

import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.RecordingCommandPort;
import com.isg.backend.violation.config.RecordingEventDeliveryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
@ConditionalOnBean(RecordingCommandPort.class)
public class RecordingEventDeliveryService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    RecordingEventDeliveryService.class
            );

    private final RecordingCommandPort recordingCommandPort;
    private final RecordingEventDeliveryProperties properties;

    public RecordingEventDeliveryService(
            RecordingCommandPort recordingCommandPort,
            RecordingEventDeliveryProperties properties
    ) {
        this.recordingCommandPort =
                recordingCommandPort;

        this.properties =
                properties;
    }

    public void deliverStart(
            ViolationStartedEvent event
    ) {
        Objects.requireNonNull(
                event,
                "event must not be null"
        );

        deliver(
                "START",
                event.commandId(),
                event.violationId(),
                () -> recordingCommandPort.startRecording(
                        event
                )
        );
    }

    public void deliverStop(
            ViolationEndedEvent event
    ) {
        Objects.requireNonNull(
                event,
                "event must not be null"
        );

        deliver(
                "STOP",
                event.commandId(),
                event.violationId(),
                () -> recordingCommandPort.stopRecording(
                        event
                )
        );
    }

    private void deliver(
            String eventType,
            UUID commandId,
            UUID violationId,
            Runnable operation
    ) {
        int maxAttempts =
                properties.getMaxAttempts();

        for (int attempt = 1;
             attempt <= maxAttempts;
             attempt++) {

            try {
                operation.run();

                if (attempt > 1) {
                    logger.info(
                            "Recording event delivery recovered. type={}, commandId={}, violationId={}, attempt={}",
                            eventType,
                            commandId,
                            violationId,
                            attempt
                    );
                }

                return;

            } catch (RuntimeException exception) {

                if (attempt == maxAttempts) {
                    logger.error(
                            "Recording event delivery failed after retries. type={}, commandId={}, violationId={}, attempts={}",
                            eventType,
                            commandId,
                            violationId,
                            maxAttempts,
                            exception
                    );

                    return;
                }

                logger.warn(
                        "Recording event delivery failed, retrying. type={}, commandId={}, violationId={}, attempt={}, maxAttempts={}",
                        eventType,
                        commandId,
                        violationId,
                        attempt,
                        maxAttempts,
                        exception
                );
            }
        }
    }
}