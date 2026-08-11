package com.isg.backend.violation.application.notification;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.application.port.DepartmentNameResolver;
import com.isg.backend.violation.application.port.NotificationRecipientResolver;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Service
@ConditionalOnBean({
        NotificationRecipientResolver.class,
        DepartmentNameResolver.class
})
public class ViolationNotificationService {

    private static final String ALERT_DESTINATION =
            "/queue/alerts";

    private final SimpMessagingTemplate messagingTemplate;
    private final NotificationRecipientResolver recipientResolver;
    private final DepartmentNameResolver departmentNameResolver;
    private final SpringDataViolationRepository violationRepository;
    private final CameraService cameraService;

    public ViolationNotificationService(
            SimpMessagingTemplate messagingTemplate,
            NotificationRecipientResolver recipientResolver,
            DepartmentNameResolver departmentNameResolver,
            SpringDataViolationRepository violationRepository,
            CameraService cameraService
    ) {
        this.messagingTemplate =
                messagingTemplate;

        this.recipientResolver =
                recipientResolver;

        this.departmentNameResolver =
                departmentNameResolver;

        this.violationRepository =
                violationRepository;

        this.cameraService =
                cameraService;
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

        CameraResponse camera =
                cameraService.getCameraById(
                        event.cameraId()
                );

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
                        camera.getName(),
                        departmentName,
                        violation.getStartedAt(),
                        violation.getConfidence()
                                .doubleValue(),
                        violation.getLifecycleStatus()
                                .name(),
                        false
                );

        for (String recipient : recipients) {
            if (recipient == null
                    || recipient.isBlank()) {
                continue;
            }

            messagingTemplate.convertAndSendToUser(
                    recipient,
                    ALERT_DESTINATION,
                    message
            );
        }
    }
}