package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.camera.infrastructure.repository.CameraSessionRepository;
import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import com.isg.backend.modules.user.service.AuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CameraServiceDepartmentAuthorizationTest {

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private CameraSessionRepository cameraSessionRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private UserRepository userRepository;

    private CameraService cameraService;

    private User user;
    private Department departmentA;
    private Department departmentB;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-08-20T00:00:00Z"),
                ZoneOffset.UTC
        );

        cameraService = new CameraService(
                cameraRepository,
                departmentRepository,
                cameraSessionRepository,
                authorizationService,
                userRepository,
                clock
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

        user = User.builder()
                .id(UUID.randomUUID())
                .email("camera-auth-test@example.com")
                .passwordHash("test-password")
                .fullName("Camera Auth Test")
                .active(true)
                .build();

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                user.getEmail(),
                                "ignored"
                        )
                );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllCamerasQueriesOnlyAccessibleDepartments() {
        Camera cameraA = camera(
                departmentA,
                "CAM-A"
        );

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(authorizationService.accessibleDepartmentIds(user.getId()))
                .thenReturn(List.of(departmentA.getId()));

        when(cameraRepository.findByDepartmentIdIn(
                List.of(departmentA.getId())
        )).thenReturn(List.of(cameraA));

        List<CameraResponse> responses =
                cameraService.getAllCameras();

        assertThat(responses).hasSize(1);

        assertThat(responses.getFirst().getId())
                .isEqualTo(cameraA.getId());

        assertThat(responses.getFirst().getDepartmentId())
                .isEqualTo(departmentA.getId());

        verify(cameraRepository)
                .findByDepartmentIdIn(
                        List.of(departmentA.getId())
                );
    }

    @Test
    void getAllCamerasReturnsEmptyWithoutQueryWhenNoDepartmentIsAccessible() {
        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(authorizationService.accessibleDepartmentIds(user.getId()))
                .thenReturn(List.of());

        List<CameraResponse> responses =
                cameraService.getAllCameras();

        assertThat(responses).isEmpty();

        verify(cameraRepository, never())
                .findByDepartmentIdIn(
                        org.mockito.ArgumentMatchers.anyList()
                );
    }

    @Test
    void getCameraByIdReturnsCameraWhenDepartmentAccessIsAllowed() {
        Camera cameraA = camera(
                departmentA,
                "CAM-A"
        );

        when(cameraRepository.findById(cameraA.getId()))
                .thenReturn(Optional.of(cameraA));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(authorizationService.canAccessDepartment(
                user.getId(),
                departmentA.getId()
        )).thenReturn(true);

        CameraResponse response =
                cameraService.getCameraById(
                        cameraA.getId()
                );

        assertThat(response.getId())
                .isEqualTo(cameraA.getId());

        assertThat(response.getDepartmentId())
                .isEqualTo(departmentA.getId());
    }

    @Test
    void getCameraByIdReturnsForbiddenForUnauthorizedDepartment() {
        Camera cameraB = camera(
                departmentB,
                "CAM-B"
        );

        when(cameraRepository.findById(cameraB.getId()))
                .thenReturn(Optional.of(cameraB));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(authorizationService.canAccessDepartment(
                user.getId(),
                departmentB.getId()
        )).thenReturn(false);

        assertThatThrownBy(
                () -> cameraService.getCameraById(
                        cameraB.getId()
                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.FORBIDDEN)
                );
    }

    private Camera camera(
            Department department,
            String code
    ) {
        return Camera.builder()
                .id(UUID.randomUUID())
                .name("Camera " + code)
                .code(code)
                .department(department)
                .active(true)
                .status(Camera.Status.ONLINE)
                .build();
    }
}