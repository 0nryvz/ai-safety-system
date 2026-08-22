package com.isg.backend.violation.application.notification;

import com.isg.backend.violation.infrastructure.notification.AuthorizationNotificationRecipientResolver;
import com.isg.backend.violation.infrastructure.notification.DatabaseDepartmentNameResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ViolationNotificationContextTest {

    @Autowired
    private ViolationNotificationService notificationService;

    @Autowired
    private ViolationNotificationEventListener notificationEventListener;

    @Autowired
    private DatabaseDepartmentNameResolver departmentNameResolver;

    @Autowired
    private AuthorizationNotificationRecipientResolver recipientResolver;


    @Test
    void notificationBeansAreCreatedBySpringContext() {

        assertThat(notificationService)
                .isNotNull();

        assertThat(notificationEventListener)
                .isNotNull();

        assertThat(departmentNameResolver)
                .isNotNull();

        assertThat(recipientResolver)
                .isNotNull();
    }
}