package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.domain.entity.CameraSession;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CameraDetailActiveSessionTest {

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
    private Department department;

    @BeforeEach
    void setUp() {
        cameraService = new CameraService(
                cameraRepository,
                departmentRepository,
                cameraSessionRepository,
                authorizationService,
                userRepository,
                Clock.fixed(
                        Instant.parse("2026-08-20T00:00:00Z"),
                        ZoneOffset.UTC
                )
        );

        department = Department.builder()
                .id(UUID.randomUUID())
                .name("Production")
                .active(true)
                .build();

        user = User.builder()
                .id(UUID.randomUUID())
                .email("camera-detail@test.local")
                .passwordHash("test-password")
                .fullName("Camera Detail Test")
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
    void getCameraByIdReturnsActiveSessionIdWhenActiveSessionExists() {
        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Camera camera = Camera.builder()
                .id(cameraId)
                .name("Production Camera")
                .code("CAM-001")
                .department(department)
                .active(true)
                .status(Camera.Status.ONLINE)
                .build();

        CameraSession activeSession = CameraSession.builder()
                .id(UUID.randomUUID())
                .camera(camera)
                .sessionId(sessionId.toString())
                .startedAt(Instant.parse("2026-08-20T00:00:00Z"))
                .status(CameraSession.SessionStatus.ACTIVE)
                .build();

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(authorizationService.canAccessDepartment(
                user.getId(),
                department.getId()
        )).thenReturn(true);

        when(cameraSessionRepository.findByCameraIdAndStatus(
                        cameraId,
                        CameraSession.SessionStatus.ACTIVE
                ))
                .thenReturn(Optional.of(activeSession));

        CameraResponse response =
                cameraService.getCameraById(cameraId);

        assertThat(response.getActiveSessionId())
                .isEqualTo(sessionId.toString());
    }

    @Test
    void getCameraByIdReturnsNullActiveSessionIdWhenNoActiveSessionExists() {
        UUID cameraId = UUID.randomUUID();

        Camera camera = Camera.builder()
                .id(cameraId)
                .name("Production Camera")
                .code("CAM-001")
                .department(department)
                .active(true)
                .status(Camera.Status.OFFLINE)
                .build();

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(userRepository.findByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        when(authorizationService.canAccessDepartment(
                user.getId(),
                department.getId()
        )).thenReturn(true);

        when(cameraSessionRepository.findByCameraIdAndStatus(
                cameraId,
                CameraSession.SessionStatus.ACTIVE
        )).thenReturn(Optional.empty());

        CameraResponse response =
                cameraService.getCameraById(cameraId);

        assertThat(response.getActiveSessionId()).isNull();
    }
}