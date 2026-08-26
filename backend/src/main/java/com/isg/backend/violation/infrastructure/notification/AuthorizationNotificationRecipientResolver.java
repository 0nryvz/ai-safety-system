package com.isg.backend.violation.infrastructure.notification;

import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.violation.application.port.NotificationRecipientResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class AuthorizationNotificationRecipientResolver
        implements NotificationRecipientResolver {

    private final UserRepository userRepository;
    private final AuthorizationService authorizationService;

    public AuthorizationNotificationRecipientResolver(
            UserRepository userRepository,
            AuthorizationService authorizationService
    ) {
        this.userRepository =
                userRepository;

        this.authorizationService =
                authorizationService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> resolveRecipients(
            UUID departmentId
    ) {
        if (departmentId == null) {
            return List.of();
        }

        return userRepository.findAll()
                .stream()
                .filter(user ->
                        user != null
                                && user.getId() != null
                )
                .filter(user ->
                        authorizationService
                                .canAccessDepartment(
                                        user.getId(),
                                        departmentId
                                )
                )
                .map(User::getEmail)
                .filter(email ->
                        email != null
                                && !email.isBlank()
                )
                .distinct()
                .toList();
    }
}