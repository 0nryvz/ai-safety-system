package com.isg.backend.modules.user.service;

import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.entity.Role;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    private AuthorizationService authorizationService;

    private Department departmentA;
    private Department departmentB;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationService(
                userRepository,
                departmentRepository
        );

        departmentA = Department.builder()
                .id(UUID.randomUUID())
                .name("Department A")
                .active(true)
                .build();

        departmentB = Department.builder()
                .id(UUID.randomUUID())
                .name("Department B")
                .active(true)
                .build();
    }

    @Test
    void adminCanAccessAnyDepartment() {
        User admin = userWithRole(
                "ADMIN",
                true,
                Set.of()
        );

        when(userRepository.findById(admin.getId()))
                .thenReturn(Optional.of(admin));

        boolean canAccess =
                authorizationService.canAccessDepartment(
                        admin.getId(),
                        departmentB.getId()
                );

        assertThat(canAccess).isTrue();
    }

    @Test
    void ohsSpecialistCanAccessAnyDepartment() {
        User specialist = userWithRole(
                "OHS_SPECIALIST",
                true,
                Set.of()
        );

        when(userRepository.findById(specialist.getId()))
                .thenReturn(Optional.of(specialist));

        boolean canAccess =
                authorizationService.canAccessDepartment(
                        specialist.getId(),
                        departmentB.getId()
                );

        assertThat(canAccess).isTrue();
    }

    @Test
    void shiftSupervisorCanAccessAssignedDepartment() {
        User supervisor = userWithRole(
                "SHIFT_SUPERVISOR",
                true,
                Set.of(departmentA)
        );

        when(userRepository.findById(supervisor.getId()))
                .thenReturn(Optional.of(supervisor));

        boolean canAccess =
                authorizationService.canAccessDepartment(
                        supervisor.getId(),
                        departmentA.getId()
                );

        assertThat(canAccess).isTrue();
    }

    @Test
    void shiftSupervisorCannotAccessUnassignedDepartment() {
        User supervisor = userWithRole(
                "SHIFT_SUPERVISOR",
                true,
                Set.of(departmentA)
        );

        when(userRepository.findById(supervisor.getId()))
                .thenReturn(Optional.of(supervisor));

        boolean canAccess =
                authorizationService.canAccessDepartment(
                        supervisor.getId(),
                        departmentB.getId()
                );

        assertThat(canAccess).isFalse();
    }

    @Test
    void unrelatedRoleCannotAccessDepartmentEvenWhenAssigned() {
        User otherUser = userWithRole(
                "USER",
                true,
                Set.of(departmentA)
        );

        when(userRepository.findById(otherUser.getId()))
                .thenReturn(Optional.of(otherUser));

        boolean canAccess =
                authorizationService.canAccessDepartment(
                        otherUser.getId(),
                        departmentA.getId()
                );

        assertThat(canAccess).isFalse();
    }

    @Test
    void inactiveUserCannotAccessDepartment() {
        User inactiveSupervisor = userWithRole(
                "SHIFT_SUPERVISOR",
                false,
                Set.of(departmentA)
        );

        when(userRepository.findById(inactiveSupervisor.getId()))
                .thenReturn(Optional.of(inactiveSupervisor));

        boolean canAccess =
                authorizationService.canAccessDepartment(
                        inactiveSupervisor.getId(),
                        departmentA.getId()
                );

        assertThat(canAccess).isFalse();
    }

    @Test
    void adminAccessibleDepartmentIdsContainsAllDepartments() {
        User admin = userWithRole(
                "ADMIN",
                true,
                Set.of()
        );

        when(userRepository.findById(admin.getId()))
                .thenReturn(Optional.of(admin));

        when(departmentRepository.findAll())
                .thenReturn(List.of(
                        departmentA,
                        departmentB
                ));

        List<UUID> accessibleIds =
                authorizationService.accessibleDepartmentIds(
                        admin.getId()
                );

        assertThat(accessibleIds)
                .containsExactlyInAnyOrder(
                        departmentA.getId(),
                        departmentB.getId()
                );
    }

    @Test
    void ohsSpecialistAccessibleDepartmentIdsContainsAllDepartments() {
        User specialist = userWithRole(
                "OHS_SPECIALIST",
                true,
                Set.of()
        );

        when(userRepository.findById(specialist.getId()))
                .thenReturn(Optional.of(specialist));

        when(departmentRepository.findAll())
                .thenReturn(List.of(
                        departmentA,
                        departmentB
                ));

        List<UUID> accessibleIds =
                authorizationService.accessibleDepartmentIds(
                        specialist.getId()
                );

        assertThat(accessibleIds)
                .containsExactlyInAnyOrder(
                        departmentA.getId(),
                        departmentB.getId()
                );
    }

    @Test
    void shiftSupervisorAccessibleDepartmentIdsContainsOnlyAssignedDepartments() {
        User supervisor = userWithRole(
                "SHIFT_SUPERVISOR",
                true,
                Set.of(departmentA)
        );

        when(userRepository.findById(supervisor.getId()))
                .thenReturn(Optional.of(supervisor));

        List<UUID> accessibleIds =
                authorizationService.accessibleDepartmentIds(
                        supervisor.getId()
                );

        assertThat(accessibleIds)
                .containsExactly(departmentA.getId());

        verify(departmentRepository, never())
                .findAll();
    }

    @Test
    void unrelatedRoleAccessibleDepartmentIdsIsEmptyEvenWhenAssigned() {
        User otherUser = userWithRole(
                "USER",
                true,
                Set.of(departmentA)
        );

        when(userRepository.findById(otherUser.getId()))
                .thenReturn(Optional.of(otherUser));

        List<UUID> accessibleIds =
                authorizationService.accessibleDepartmentIds(
                        otherUser.getId()
                );

        assertThat(accessibleIds).isEmpty();

        verify(departmentRepository, never())
                .findAll();
    }

    @Test
    void inactiveUserAccessibleDepartmentIdsIsEmpty() {
        User inactiveSupervisor = userWithRole(
                "SHIFT_SUPERVISOR",
                false,
                Set.of(departmentA)
        );

        when(userRepository.findById(inactiveSupervisor.getId()))
                .thenReturn(Optional.of(inactiveSupervisor));

        List<UUID> accessibleIds =
                authorizationService.accessibleDepartmentIds(
                        inactiveSupervisor.getId()
                );

        assertThat(accessibleIds).isEmpty();

        verify(departmentRepository, never())
                .findAll();
    }

    private User userWithRole(
            String roleName,
            boolean active,
            Set<Department> departments
    ) {
        Role role = Role.builder()
                .id(UUID.randomUUID())
                .name(roleName)
                .build();

        return User.builder()
                .id(UUID.randomUUID())
                .email(
                        UUID.randomUUID()
                                + "@test.local"
                )
                .passwordHash("test-password-hash")
                .fullName("Test User")
                .active(active)
                .roles(Set.of(role))
                .departments(departments)
                .build();
    }
}