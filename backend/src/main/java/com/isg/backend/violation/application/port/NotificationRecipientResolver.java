package com.isg.backend.violation.application.port;

import java.util.List;
import java.util.UUID;

public interface NotificationRecipientResolver {

    List<String> resolveRecipients(
            UUID departmentId
    );
}