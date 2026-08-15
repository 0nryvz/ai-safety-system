package com.isg.backend.violation.infrastructure.notification;

import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import com.isg.backend.modules.user.service.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorizationNotificationRecipientResolverTest {

    private UserRepository userRepository;
    private AuthorizationService authorizationService;

    private AuthorizationNotificationRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        userRepository =
                mock(UserRepository.class);

        authorizationService =
                mock(AuthorizationService.class);

        resolver =
                new AuthorizationNotificationRecipientResolver(
                        userRepository,
                        authorizationService
                );
    }

    @Test
    void returnsOnlyUsersAuthorizedForDepartment() {
        UUID departmentId =
                UUID.randomUUID();

        UUID authorizedUserId =
                UUID.randomUUID();

        UUID unauthorizedUserId =
                UUID.randomUUID();

        UUID inaccessibleUserId =
                UUID.randomUUID();

        User authorizedUser =
                mock(User.class);

        User unauthorizedUser =
                mock(User.class);

        User inaccessibleUser =
                mock(User.class);

        when(authorizedUser.getId())
                .thenReturn(
                        authorizedUserId
                );

        when(authorizedUser.getEmail())
                .thenReturn(
                        "authorized@example.com"
                );

        when(unauthorizedUser.getId())
                .thenReturn(
                        unauthorizedUserId
                );

        when(unauthorizedUser.getEmail())
                .thenReturn(
                        "unauthorized@example.com"
                );

        when(inaccessibleUser.getId())
                .thenReturn(
                        inaccessibleUserId
                );

        when(inaccessibleUser.getEmail())
                .thenReturn(
                        "inactive@example.com"
                );

        when(userRepository.findAll())
                .thenReturn(
                        List.of(
                                authorizedUser,
                                unauthorizedUser,
                                inaccessibleUser
                        )
                );

        when(
                authorizationService
                        .canAccessDepartment(
                                authorizedUserId,
                                departmentId
                        )
        ).thenReturn(
                true
        );

        when(
                authorizationService
                        .canAccessDepartment(
                                unauthorizedUserId,
                                departmentId
                        )
        ).thenReturn(
                false
        );

        when(
                authorizationService
                        .canAccessDepartment(
                                inaccessibleUserId,
                                departmentId
                        )
        ).thenReturn(
                false
        );

        List<String> recipients =
                resolver.resolveRecipients(
                        departmentId
                );

        assertThat(recipients)
                .containsExactly(
                        "authorized@example.com"
                );

        verify(
                authorizationService
        ).canAccessDepartment(
                authorizedUserId,
                departmentId
        );

        verify(
                authorizationService
        ).canAccessDepartment(
                unauthorizedUserId,
                departmentId
        );

        verify(
                authorizationService
        ).canAccessDepartment(
                inaccessibleUserId,
                departmentId
        );
    }

    @Test
    void returnsEmptyListWhenDepartmentIdIsNull() {
        List<String> recipients =
                resolver.resolveRecipients(
                        null
                );

        assertThat(recipients)
                .isEmpty();

        verify(
                userRepository,
                never()
        ).findAll();
    }

    @Test
    void removesDuplicateRecipientEmails() {
        UUID departmentId =
                UUID.randomUUID();

        UUID firstUserId =
                UUID.randomUUID();

        UUID secondUserId =
                UUID.randomUUID();

        User firstUser =
                mock(User.class);

        User secondUser =
                mock(User.class);

        when(firstUser.getId())
                .thenReturn(
                        firstUserId
                );

        when(firstUser.getEmail())
                .thenReturn(
                        "same@example.com"
                );

        when(secondUser.getId())
                .thenReturn(
                        secondUserId
                );

        when(secondUser.getEmail())
                .thenReturn(
                        "same@example.com"
                );

        when(userRepository.findAll())
                .thenReturn(
                        List.of(
                                firstUser,
                                secondUser
                        )
                );

        when(
                authorizationService
                        .canAccessDepartment(
                                firstUserId,
                                departmentId
                        )
        ).thenReturn(
                true
        );

        when(
                authorizationService
                        .canAccessDepartment(
                                secondUserId,
                                departmentId
                        )
        ).thenReturn(
                true
        );

        List<String> recipients =
                resolver.resolveRecipients(
                        departmentId
                );

        assertThat(recipients)
                .containsExactly(
                        "same@example.com"
                );
    }

    @Test
    void ignoresAuthorizedUserWithBlankEmail() {
        UUID departmentId =
                UUID.randomUUID();

        UUID userId =
                UUID.randomUUID();

        User user =
                mock(User.class);

        when(user.getId())
                .thenReturn(
                        userId
                );

        when(user.getEmail())
                .thenReturn(
                        "   "
                );

        when(userRepository.findAll())
                .thenReturn(
                        List.of(
                                user
                        )
                );

        when(
                authorizationService
                        .canAccessDepartment(
                                userId,
                                departmentId
                        )
        ).thenReturn(
                true
        );

        List<String> recipients =
                resolver.resolveRecipients(
                        departmentId
                );

        assertThat(recipients)
                .isEmpty();
    }

    @Test
    void ignoresUserWithoutId() {
        UUID departmentId =
                UUID.randomUUID();

        User user =
                mock(User.class);

        when(user.getId())
                .thenReturn(
                        null
                );

        when(userRepository.findAll())
                .thenReturn(
                        List.of(
                                user
                        )
                );

        List<String> recipients =
                resolver.resolveRecipients(
                        departmentId
                );

        assertThat(recipients)
                .isEmpty();

        verify(
                authorizationService,
                never()
        ).canAccessDepartment(
                null,
                departmentId
        );
    }
}