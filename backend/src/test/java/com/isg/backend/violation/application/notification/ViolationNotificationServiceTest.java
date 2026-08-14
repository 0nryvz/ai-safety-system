package com.isg.backend.violation.application.notification;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.violation.application.event.ViolationRecordingUpdatedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.DepartmentNameResolver;
import com.isg.backend.violation.application.port.NotificationRecipientResolver;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ViolationNotificationServiceTest {

    private SimpMessagingTemplate messagingTemplate;
    private NotificationRecipientResolver recipientResolver;
    private DepartmentNameResolver departmentNameResolver;
    private SpringDataViolationRepository violationRepository;
    private CameraService cameraService;
    private SimpleMeterRegistry meterRegistry;

    private ViolationNotificationService service;

    @BeforeEach
    void setUp() {
        messagingTemplate =
                mock(SimpMessagingTemplate.class);

        recipientResolver =
                mock(NotificationRecipientResolver.class);

        departmentNameResolver =
                mock(DepartmentNameResolver.class);

        violationRepository =
                mock(SpringDataViolationRepository.class);

        cameraService =
                mock(CameraService.class);

        meterRegistry =
                new SimpleMeterRegistry();

        service =
                new ViolationNotificationService(
                        messagingTemplate,
                        recipientResolver,
                        departmentNameResolver,
                        violationRepository,
                        cameraService,
                        meterRegistry
                );
    }

    @Test
    void sendsInitialAlertToEveryResolvedRecipientAndRecordsLatency() {
        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now()
                        .minusSeconds(2);

        Instant confirmedAt =
                Instant.now()
                        .minusMillis(250);

        ViolationJpaEntity violation =
                initialViolation(
                        violationId,
                        departmentId,
                        startedAt
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(cameraService.getCameraById(
                cameraId
        )).thenReturn(
                CameraResponse.builder()
                        .id(cameraId)
                        .name("Kaynak Kamera 1")
                        .departmentId(departmentId)
                        .build()
        );

        when(departmentNameResolver.resolveDepartmentName(
                departmentId
        )).thenReturn(
                "Kaynak Bölümü"
        );

        when(recipientResolver.resolveRecipients(
                departmentId
        )).thenReturn(
                List.of(
                        "expert@example.com",
                        "admin@example.com"
                )
        );

        ViolationStartedEvent event =
                new ViolationStartedEvent(
                        UUID.randomUUID(),
                        violationId,
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_WELDING_MASK,
                        startedAt,
                        confirmedAt
                );

        service.sendViolationStarted(
                event
        );

        ArgumentCaptor<AlertMessage> messageCaptor =
                ArgumentCaptor.forClass(
                        AlertMessage.class
                );

        verify(
                messagingTemplate,
                times(2)
        ).convertAndSendToUser(
                anyString(),
                eq("/queue/alerts"),
                messageCaptor.capture()
        );

        AlertMessage message =
                messageCaptor.getValue();

        assertThat(message.violationId())
                .isEqualTo(
                        violationId
                );

        assertThat(message.type())
                .isEqualTo(
                        ViolationType.MISSING_WELDING_MASK
                );

        assertThat(message.cameraName())
                .isEqualTo(
                        "Kaynak Kamera 1"
                );

        assertThat(message.departmentName())
                .isEqualTo(
                        "Kaynak Bölümü"
                );

        assertThat(message.recordingStatus())
                .isEqualTo(
                        "REQUESTED"
                );

        assertThat(message.clipReady())
                .isFalse();

        Timer timer =
                meterRegistry.find(
                                "isg.violation.notification.latency"
                        )
                        .tag(
                                "channel",
                                "websocket"
                        )
                        .timer();

        assertThat(timer)
                .isNotNull();

        assertThat(timer.count())
                .isEqualTo(
                        1
                );

        assertThat(
                timer.max(
                        TimeUnit.MILLISECONDS
                )
        )
                .isLessThanOrEqualTo(
                        2000.0
                );
    }

    @Test
    void websocketFailureForOneRecipientDoesNotPreventOtherRecipients() {
        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now()
                        .minusSeconds(1);

        Instant confirmedAt =
                Instant.now()
                        .minusMillis(100);

        ViolationJpaEntity violation =
                initialViolation(
                        violationId,
                        departmentId,
                        startedAt
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(cameraService.getCameraById(
                cameraId
        )).thenReturn(
                CameraResponse.builder()
                        .id(cameraId)
                        .name("Kaynak Kamera 1")
                        .departmentId(departmentId)
                        .build()
        );

        when(departmentNameResolver.resolveDepartmentName(
                departmentId
        )).thenReturn(
                "Kaynak Bölümü"
        );

        when(recipientResolver.resolveRecipients(
                departmentId
        )).thenReturn(
                List.of(
                        "broken@example.com",
                        "healthy@example.com"
                )
        );

        doThrow(
                new RuntimeException(
                        "WebSocket unavailable"
                )
        ).when(
                messagingTemplate
        ).convertAndSendToUser(
                eq("broken@example.com"),
                eq("/queue/alerts"),
                any()
        );

        service.sendViolationStarted(
                new ViolationStartedEvent(
                        UUID.randomUUID(),
                        violationId,
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_WELDING_MASK,
                        startedAt,
                        confirmedAt
                )
        );

        verify(messagingTemplate)
                .convertAndSendToUser(
                        eq("broken@example.com"),
                        eq("/queue/alerts"),
                        any()
                );

        verify(messagingTemplate)
                .convertAndSendToUser(
                        eq("healthy@example.com"),
                        eq("/queue/alerts"),
                        any()
                );

        Timer timer =
                meterRegistry.find(
                                "isg.violation.notification.latency"
                        )
                        .timer();

        assertThat(timer)
                .isNotNull();

        assertThat(timer.count())
                .isEqualTo(
                        1
                );
    }

    @Test
    void doesNotRecordSuccessfulDeliveryLatencyWhenAllDeliveriesFail() {
        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now()
                        .minusSeconds(1);

        ViolationJpaEntity violation =
                initialViolation(
                        violationId,
                        departmentId,
                        startedAt
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(cameraService.getCameraById(
                cameraId
        )).thenReturn(
                CameraResponse.builder()
                        .id(cameraId)
                        .name("Kaynak Kamera 1")
                        .departmentId(departmentId)
                        .build()
        );

        when(departmentNameResolver.resolveDepartmentName(
                departmentId
        )).thenReturn(
                "Kaynak Bölümü"
        );

        when(recipientResolver.resolveRecipients(
                departmentId
        )).thenReturn(
                List.of(
                        "broken@example.com"
                )
        );

        doThrow(
                new RuntimeException(
                        "WebSocket unavailable"
                )
        ).when(
                messagingTemplate
        ).convertAndSendToUser(
                eq("broken@example.com"),
                eq("/queue/alerts"),
                any()
        );

        service.sendViolationStarted(
                new ViolationStartedEvent(
                        UUID.randomUUID(),
                        violationId,
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_WELDING_MASK,
                        startedAt,
                        Instant.now()
                                .minusMillis(100)
                )
        );

        Timer timer =
                meterRegistry.find(
                                "isg.violation.notification.latency"
                        )
                        .timer();

        assertThat(timer)
                .isNotNull();

        assertThat(timer.count())
                .isZero();
    }

    @Test
    void sendsReadyUpdateWithSameViolationId() {
        UUID violationId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant updatedAt =
                Instant.now();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(recipientResolver.resolveRecipients(
                departmentId
        )).thenReturn(
                List.of(
                        "expert@example.com"
                )
        );

        service.sendViolationUpdate(
                new ViolationRecordingUpdatedEvent(
                        violationId,
                        "COMPLETED",
                        "READY",
                        true,
                        updatedAt,
                        null
                )
        );

        ArgumentCaptor<ViolationUpdateMessage> messageCaptor =
                ArgumentCaptor.forClass(
                        ViolationUpdateMessage.class
                );

        verify(messagingTemplate)
                .convertAndSendToUser(
                        eq("expert@example.com"),
                        eq("/queue/alerts"),
                        messageCaptor.capture()
                );

        ViolationUpdateMessage message =
                messageCaptor.getValue();

        assertThat(message.violationId())
                .isEqualTo(
                        violationId
                );

        assertThat(message.lifecycleStatus())
                .isEqualTo(
                        "COMPLETED"
                );

        assertThat(message.recordingStatus())
                .isEqualTo(
                        "READY"
                );

        assertThat(message.clipReady())
                .isTrue();

        assertThat(message.errorCode())
                .isNull();
    }

    @Test
    void sendsErrorUpdateWithErrorCode() {
        UUID violationId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant updatedAt =
                Instant.now();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(recipientResolver.resolveRecipients(
                departmentId
        )).thenReturn(
                List.of(
                        "expert@example.com"
                )
        );

        service.sendViolationUpdate(
                new ViolationRecordingUpdatedEvent(
                        violationId,
                        "ERROR",
                        "ERROR",
                        false,
                        updatedAt,
                        "ENCODER_FAILED"
                )
        );

        ArgumentCaptor<ViolationUpdateMessage> messageCaptor =
                ArgumentCaptor.forClass(
                        ViolationUpdateMessage.class
                );

        verify(messagingTemplate)
                .convertAndSendToUser(
                        eq("expert@example.com"),
                        eq("/queue/alerts"),
                        messageCaptor.capture()
                );

        ViolationUpdateMessage message =
                messageCaptor.getValue();

        assertThat(message.violationId())
                .isEqualTo(
                        violationId
                );

        assertThat(message.lifecycleStatus())
                .isEqualTo(
                        "ERROR"
                );

        assertThat(message.recordingStatus())
                .isEqualTo(
                        "ERROR"
                );

        assertThat(message.clipReady())
                .isFalse();

        assertThat(message.errorCode())
                .isEqualTo(
                        "ENCODER_FAILED"
                );
    }

    @Test
    void doesNotSendWhenNoRecipientsAreResolved() {
        UUID violationId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(recipientResolver.resolveRecipients(
                departmentId
        )).thenReturn(
                List.of()
        );

        service.sendViolationUpdate(
                new ViolationRecordingUpdatedEvent(
                        violationId,
                        "COMPLETED",
                        "READY",
                        true,
                        Instant.now(),
                        null
                )
        );

        verify(
                messagingTemplate,
                never()
        ).convertAndSendToUser(
                anyString(),
                anyString(),
                any()
        );
    }

    private ViolationJpaEntity initialViolation(
            UUID violationId,
            UUID departmentId,
            Instant startedAt
    ) {
        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(violation.getId())
                .thenReturn(
                        violationId
                );

        when(violation.getDepartmentId())
                .thenReturn(
                        departmentId
                );

        when(violation.getViolationType())
                .thenReturn(
                        ViolationType.MISSING_WELDING_MASK
                );

        when(violation.getStartedAt())
                .thenReturn(
                        startedAt
                );

        when(violation.getConfidence())
                .thenReturn(
                        new BigDecimal(
                                "0.9200"
                        )
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.ACTIVE
                );

        return violation;
    }
}