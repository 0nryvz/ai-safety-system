package com.isg.backend.violation.application.notification;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.DepartmentNameResolver;
import com.isg.backend.violation.application.port.NotificationRecipientResolver;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
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

        service =
                new ViolationNotificationService(
                        messagingTemplate,
                        recipientResolver,
                        departmentNameResolver,
                        violationRepository,
                        cameraService
                );
    }

    @Test
    void sendsAlertToEveryResolvedRecipient() {
        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now();

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

        when(violationRepository.findById(
                violationId
        )).thenReturn(
                Optional.of(
                        violation
                )
        );

        CameraResponse camera =
                CameraResponse.builder()
                        .id(cameraId)
                        .name("Kaynak Kamera 1")
                        .departmentId(departmentId)
                        .build();

        when(cameraService.getCameraById(
                cameraId
        )).thenReturn(
                camera
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
                        startedAt
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
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(
                        "/queue/alerts"
                ),
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

        assertThat(message.confidence())
                .isEqualTo(
                        0.92
                );

        assertThat(message.lifecycleStatus())
                .isEqualTo(
                        "ACTIVE"
                );

        assertThat(message.coverImageReady())
                .isFalse();
    }

    @Test
    void doesNotSendWhenNoRecipientsAreResolved() {
        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.now();

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
                                "0.9000"
                        )
                );

        when(violation.getLifecycleStatus())
                .thenReturn(
                        ViolationLifecycleStatus.ACTIVE
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
                List.of()
        );

        service.sendViolationStarted(
                new ViolationStartedEvent(
                        UUID.randomUUID(),
                        violationId,
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_WELDING_MASK,
                        startedAt
                )
        );

        verify(
                messagingTemplate,
                never()
        ).convertAndSendToUser(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any()
        );
    }
}