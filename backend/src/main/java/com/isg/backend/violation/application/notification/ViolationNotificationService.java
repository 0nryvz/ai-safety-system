package com.isg.backend.violation.application.notification;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.application.event.ViolationRecordingUpdatedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.DepartmentNameResolver;
import com.isg.backend.violation.application.port.NotificationRecipientResolver;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

@Service
public class ViolationNotificationService {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    ViolationNotificationService.class
            );

    private static final String ALERT_DESTINATION =
            "/queue/alerts";

    private static final String INITIAL_ALERT_LATENCY_METRIC =
            "isg.violation.notification.latency";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRecipientResolver recipientResolver;
    private final DepartmentNameResolver departmentNameResolver;
    private final SpringDataViolationRepository violationRepository;
    private final CameraQueryService cameraQueryService;
    private final Timer initialAlertLatencyTimer;
    private final Clock clock;

    public ViolationNotificationService(
            SimpMessagingTemplate messagingTemplate,
            NotificationRecipientResolver recipientResolver,
            DepartmentNameResolver departmentNameResolver,
            SpringDataViolationRepository violationRepository,
            CameraQueryService cameraQueryService,
            MeterRegistry meterRegistry,
            Clock clock
    ) {
        this.messagingTemplate =
                messagingTemplate;

        this.recipientResolver =
                recipientResolver;

        this.departmentNameResolver =
                departmentNameResolver;

        this.violationRepository =
                violationRepository;

        this.cameraQueryService =
                cameraQueryService;

        this.clock =
                clock;

        this.initialAlertLatencyTimer =
                Timer.builder(
                                INITIAL_ALERT_LATENCY_METRIC
                        )
                        .description(
                                "Time from violation confirmation to first successful WebSocket alert dispatch"
                        )
                        .tag(
                                "channel",
                                "websocket"
                        )
                        .register(
                                meterRegistry
                        );
    }

    public void sendViolationStarted(
            ViolationStartedEvent event
    ) {
        Objects.requireNonNull(
                event,
                "event must not be null"
        );

        ViolationJpaEntity violation =
                violationRepository.findById(
                        event.violationId()
                ).orElseThrow(
                        () -> new IllegalStateException(
                                "Violation not found: "
                                        + event.violationId()
                        )
                );

        String cameraName =
                cameraQueryService.findCameraName(
                        event.cameraId()
                ).orElseGet(() -> {
                    logger.warn(
                            "Camera name not found for notification. cameraId={}",
                            event.cameraId()
                    );
                    return "Unknown Camera";
                });

        String departmentName =
                departmentNameResolver.resolveDepartmentName(
                        violation.getDepartmentId()
                );

        List<String> recipients =
                recipientResolver.resolveRecipients(
                        violation.getDepartmentId()
                );

        AlertMessage message =
                new AlertMessage(
                        violation.getId(),
                        violation.getViolationType(),
                        cameraName,
                        departmentName,
                        violation.getStartedAt(),
                        violation.getConfidence()
                                .doubleValue(),
                        violation.getLifecycleStatus()
                                .name(),
                        "REQUESTED",
                        false,
                        false
                );

        boolean dispatched =
                sendToRecipients(
                        recipients,
                        message,
                        event.violationId()
                );

        if (dispatched) {
            Instant notificationSentAt =
                    Instant.now(
                            clock
                    );

            violation.markAlertSent(
                    notificationSentAt
            );

            violationRepository.save(
                    violation
            );

            Duration latency =
                    Duration.between(
                            event.confirmedAt(),
                            notificationSentAt
                    );

            if (latency.isNegative()) {
                latency =
                        Duration.ZERO;
            }

            initialAlertLatencyTimer.record(
                    latency
            );

            if (latency.compareTo(
                    Duration.ofSeconds(2)
            ) > 0) {
                logger.warn(
                        "Violation initial alert exceeded latency target. violationId={}, latencyMs={}",
                        event.violationId(),
                        latency.toMillis()
                );
            }
        }
    }

    public void sendViolationUpdate(
            ViolationRecordingUpdatedEvent event
    ) {
        Objects.requireNonNull(
                event,
                "event must not be null"
        );

        ViolationJpaEntity violation =
                violationRepository.findById(
                        event.violationId()
                ).orElseThrow(
                        () -> new IllegalStateException(
                                "Violation not found: "
                                        + event.violationId()
                        )
                );

        List<String> recipients =
                recipientResolver.resolveRecipients(
                        violation.getDepartmentId()
                );

        ViolationUpdateMessage message =
                new ViolationUpdateMessage(
                        event.violationId(),
                        event.lifecycleStatus(),
                        event.recordingStatus(),
                        event.clipReady(),
                        event.updatedAt(),
                        event.errorCode()
                );

        sendToRecipients(
                recipients,
                message,
                event.violationId()
        );
    }

    private boolean sendToRecipients(
            List<String> recipients,
            Object message,
            java.util.UUID violationId
    ) {
        if (recipients == null || recipients.isEmpty()) {
            return false;
        }

        boolean dispatched =
                false;

        for (String recipient : recipients) {
            if (recipient == null || recipient.isBlank()) {
                continue;
            }

            try {
                messagingTemplate.convertAndSendToUser(
                        recipient,
                        ALERT_DESTINATION,
                        message
                );

                dispatched =
                        true;

            } catch (RuntimeException exception) {
                logger.error(
                        "WebSocket violation notification dispatch failed. violationId={}, recipient={}",
                        violationId,
                        recipient,
                        exception
                );
            }
        }

        return dispatched;
    }
}
