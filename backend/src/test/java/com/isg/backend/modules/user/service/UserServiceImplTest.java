package com.isg.backend.modules.user.service;

import com.isg.backend.modules.user.dto.CreateUserRequest;
import com.isg.backend.modules.user.dto.UpdateUserRequest;
import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.entity.Role;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.modules.user.infrastructure.RoleRepository;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthorizationService authorizationService;

    private UserServiceImpl userService;

    @BeforeEach
    void setUp() {
        userService = new UserServiceImpl(
                userRepository,
                roleRepository,
                departmentRepository,
                passwordEncoder,
                authorizationService
        );
    }

    @Test
    void duplicateEmailReturnsConflictAndDoesNotPersist() {
        CreateUserRequest request =
                org.mockito.Mockito.mock(CreateUserRequest.class);

        when(request.getEmail())
                .thenReturn("existing@test.local");

        when(userRepository.existsByEmail("existing@test.local"))
                .thenReturn(true);

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.createUser(request)
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);

        verify(userRepository, never())
                .save(any(User.class));

        verify(passwordEncoder, never())
                .encode(any());
    }

    @Test
    void createUserEncodesPasswordAndAssignsRoleAndDepartment() {
        CreateUserRequest request =
                org.mockito.Mockito.mock(CreateUserRequest.class);

        UUID departmentId = UUID.randomUUID();

        Role role = Role.builder()
                .id(UUID.randomUUID())
                .name("SHIFT_SUPERVISOR")
                .build();

        Department department = Department.builder()
                .id(departmentId)
                .name("Production")
                .active(true)
                .build();

        when(request.getEmail())
                .thenReturn("new.user@test.local");

        when(request.getPassword())
                .thenReturn("StrongPassword123!");

        when(request.getFullName())
                .thenReturn("New User");

        when(request.getRoleNames())
                .thenReturn(Set.of("SHIFT_SUPERVISOR"));

        when(request.getDepartmentIds())
                .thenReturn(Set.of(departmentId));

        when(userRepository.existsByEmail("new.user@test.local"))
                .thenReturn(false);

        when(roleRepository.findByName("SHIFT_SUPERVISOR"))
                .thenReturn(Optional.of(role));

        when(departmentRepository.findAllById(Set.of(departmentId)))
                .thenReturn(List.of(department));

        when(passwordEncoder.encode("StrongPassword123!"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.createUser(request);

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .save(captor.capture());

        User saved = captor.getValue();

        assertThat(saved.getEmail())
                .isEqualTo("new.user@test.local");

        assertThat(saved.getPasswordHash())
                .isEqualTo("encoded-password");

        assertThat(saved.getFullName())
                .isEqualTo("New User");

        assertThat(saved.isActive())
                .isTrue();

        assertThat(saved.getRoles())
                .containsExactly(role);

        assertThat(saved.getDepartments())
                .containsExactly(department);

        verify(passwordEncoder)
                .encode("StrongPassword123!");
    }

    @Test
    void createUserRejectsInactiveDepartment() {
        CreateUserRequest request =
                org.mockito.Mockito.mock(CreateUserRequest.class);

        UUID departmentId = UUID.randomUUID();

        Role role = Role.builder()
                .id(UUID.randomUUID())
                .name("SHIFT_SUPERVISOR")
                .build();

        Department inactiveDepartment = Department.builder()
                .id(departmentId)
                .code("KAYNAK-PASIF")
                .name("Pasif Departman")
                .active(false)
                .build();

        when(request.getEmail())
                .thenReturn("inactive.department@test.local");

        when(request.getPassword())
                .thenReturn("StrongPassword123!");

        when(request.getFullName())
                .thenReturn("Inactive Department User");

        when(request.getRoleNames())
                .thenReturn(Set.of("SHIFT_SUPERVISOR"));

        when(request.getDepartmentIds())
                .thenReturn(Set.of(departmentId));

        when(userRepository.existsByEmail("inactive.department@test.local"))
                .thenReturn(false);

        when(roleRepository.findByName("SHIFT_SUPERVISOR"))
                .thenReturn(Optional.of(role));

        when(passwordEncoder.encode("StrongPassword123!"))
                .thenReturn("encoded-password");

        when(departmentRepository.findAllById(Set.of(departmentId)))
                .thenReturn(List.of(inactiveDepartment));


        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.createUser(request)
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(userRepository, never())
                .save(any(User.class));
    }

    @Test
    void updateUserRejectsNewInactiveDepartmentAndKeepsExistingDepartments() {
        UUID userId = UUID.randomUUID();
        UUID existingDepartmentId = UUID.randomUUID();
        UUID inactiveDepartmentId = UUID.randomUUID();

        Department existingDepartment = Department.builder()
                .id(existingDepartmentId)
                .code("MEVCUT")
                .name("Mevcut Departman")
                .active(true)
                .build();

        Department inactiveDepartment = Department.builder()
                .id(inactiveDepartmentId)
                .code("PASIF")
                .name("Pasif Departman")
                .active(false)
                .build();

        User target = user(
                userId,
                "worker@test.local",
                "SHIFT_SUPERVISOR",
                true
        );

        target.getDepartments().add(existingDepartment);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setDepartmentIds(Set.of(inactiveDepartmentId));

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(target));

        when(departmentRepository.findAllById(Set.of(inactiveDepartmentId)))
                .thenReturn(List.of(inactiveDepartment));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.updateUser(
                                userId,
                                request,
                                "admin@test.local"
                        )
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(target.getDepartments())
                .containsExactly(existingDepartment);

        verify(userRepository, never())
                .save(any(User.class));
    }

    @Test
    void updateUserAllowsKeepingExistingInactiveDepartment() {
        UUID userId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        Department existingInactiveDepartment = Department.builder()
                .id(departmentId)
                .code("MEVCUT-PASIF")
                .name("Mevcut Pasif Departman")
                .active(false)
                .build();

        User target = user(
                userId,
                "worker@test.local",
                "SHIFT_SUPERVISOR",
                true
        );

        target.getDepartments().add(existingInactiveDepartment);

        UpdateUserRequest request = new UpdateUserRequest();
        request.setDepartmentIds(Set.of(departmentId));

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(target));

        when(departmentRepository.findAllById(Set.of(departmentId)))
                .thenReturn(List.of(existingInactiveDepartment));

        when(userRepository.save(target))
                .thenReturn(target);

        userService.updateUser(
                userId,
                request,
                "admin@test.local"
        );

        assertThat(target.getDepartments())
                .containsExactly(existingInactiveDepartment);

        verify(userRepository)
                .save(target);
    }

    @Test
    void unknownRoleReturnsNotFoundAndDoesNotPersist() {
        CreateUserRequest request =
                org.mockito.Mockito.mock(CreateUserRequest.class);

        when(request.getEmail())
                .thenReturn("new.user@test.local");

        when(request.getRoleNames())
                .thenReturn(Set.of("UNKNOWN_ROLE"));

        when(userRepository.existsByEmail("new.user@test.local"))
                .thenReturn(false);

        when(roleRepository.findByName("UNKNOWN_ROLE"))
                .thenReturn(Optional.empty());

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.createUser(request)
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verify(userRepository, never())
                .save(any(User.class));

        verify(passwordEncoder, never())
                .encode(any());
    }

    @Test
    void deactivateUserUsesSoftDelete() {
        UUID userId = UUID.randomUUID();

        User user = user(
                userId,
                "worker@test.local",
                "SHIFT_SUPERVISOR",
                true
        );

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        userService.deactivateUser(userId, "admin@test.local");

        assertThat(user.isActive())
                .isFalse();

        verify(userRepository)
                .save(user);

        verify(userRepository, never())
                .delete(any(User.class));

        verify(userRepository, never())
                .deleteById(any(UUID.class));
    }

    @Test
    void lastActiveAdminCannotBeDeactivated() {
        UUID adminId = UUID.randomUUID();

        User admin = user(
                adminId,
                "admin@test.local",
                "ADMIN",
                true
        );

        when(userRepository.findById(adminId))
                .thenReturn(Optional.of(admin));

        when(userRepository.findAll())
                .thenReturn(List.of(admin));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.deactivateUser(adminId, "operator@test.local")
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(admin.isActive())
                .isTrue();

        verify(userRepository, never())
                .save(admin);
    }

    @Test
    void adminCanBeDeactivatedWhenAnotherActiveAdminExists() {
        UUID targetId = UUID.randomUUID();

        User target = user(
                targetId,
                "admin.one@test.local",
                "ADMIN",
                true
        );

        User otherAdmin = user(
                UUID.randomUUID(),
                "admin.two@test.local",
                "ADMIN",
                true
        );

        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        when(userRepository.findAll())
                .thenReturn(List.of(target, otherAdmin));

        userService.deactivateUser(targetId, "admin.two@test.local");

        assertThat(target.isActive())
                .isFalse();

        verify(userRepository)
                .save(target);
    }

    @Test
    void createUserNormalizesEmailBeforeDuplicateCheckAndPersistence() {
        CreateUserRequest request =
                org.mockito.Mockito.mock(CreateUserRequest.class);

        Role role = Role.builder()
                .id(UUID.randomUUID())
                .name("SHIFT_SUPERVISOR")
                .build();

        when(request.getEmail())
                .thenReturn("New.User@Test.Local");

        when(request.getPassword())
                .thenReturn("StrongPassword123!");

        when(request.getFullName())
                .thenReturn("Normalized User");

        when(request.getRoleNames())
                .thenReturn(Set.of("SHIFT_SUPERVISOR"));

        when(request.getDepartmentIds())
                .thenReturn(Set.of());

        when(roleRepository.findByName("SHIFT_SUPERVISOR"))
                .thenReturn(Optional.of(role));

        when(passwordEncoder.encode("StrongPassword123!"))
                .thenReturn("encoded-password");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        userService.createUser(request);

        verify(userRepository)
                .existsByEmail("new.user@test.local");

        ArgumentCaptor<User> captor =
                ArgumentCaptor.forClass(User.class);

        verify(userRepository)
                .save(captor.capture());

        assertThat(captor.getValue().getEmail())
                .isEqualTo("new.user@test.local");
    }
    @Test
    void userCannotDeactivateOwnAccount() {
        UUID targetId = UUID.randomUUID();

        User target = user(
                targetId,
                "admin.one@test.local",
                "ADMIN",
                true
        );


        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.deactivateUser(
                                targetId,
                                "ADMIN.ONE@Test.Local"
                        )
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(target.isActive())
                .isTrue();

        verify(userRepository, never())
                .findAll();

        verify(userRepository, never())
                .save(target);
    }

    @Test
    void userCannotDeactivateOwnAccountThroughPatch() {
        UUID targetId = UUID.randomUUID();

        User target = user(
                targetId,
                "admin.one@test.local",
                "ADMIN",
                true
        );

        UpdateUserRequest request = new UpdateUserRequest();
        request.setActive(false);

        when(userRepository.findById(targetId))
                .thenReturn(Optional.of(target));

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> userService.updateUser(
                                targetId,
                                request,
                                "ADMIN.ONE@Test.Local"
                        )
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(target.isActive())
                .isTrue();

        verify(userRepository, never())
                .findAll();

        verify(userRepository, never())
                .save(target);
    }
    private User user(
            UUID id,
            String email,
            String roleName,
            boolean active
    ) {
        Role role = Role.builder()
                .id(UUID.randomUUID())
                .name(roleName)
                .build();

        return User.builder()
                .id(id)
                .email(email)
                .passwordHash("encoded-password")
                .fullName("Test User")
                .active(active)
                .roles(Set.of(role))
                .build();
    }
}