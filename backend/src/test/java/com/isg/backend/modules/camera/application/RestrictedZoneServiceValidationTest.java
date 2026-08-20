package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.api.dto.PointDto;
import com.isg.backend.modules.camera.api.dto.RestrictedZoneUpdateReq;
import com.isg.backend.modules.camera.domain.RestrictedZone;
import com.isg.backend.modules.camera.domain.RestrictedZoneRepository;
import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import com.isg.backend.modules.user.service.AuthorizationService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestrictedZoneServiceValidationTest {

    private static final String USER_EMAIL = "be2@test.local";

    @Mock
    private RestrictedZoneRepository restrictedZoneRepository;

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private AuthorizationService authorizationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private User user;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private Camera camera;

    private RestrictedZoneService restrictedZoneService;

    private UUID cameraId;
    private UUID userId;
    private UUID departmentId;

    @BeforeEach
    void setUp() {
        restrictedZoneService =
                new RestrictedZoneService(
                        restrictedZoneRepository,
                        cameraRepository,
                        authorizationService,
                        userRepository
                );

        cameraId = UUID.randomUUID();
        userId = UUID.randomUUID();
        departmentId = UUID.randomUUID();

        SecurityContextHolder.getContext()
                .setAuthentication(
                        new UsernamePasswordAuthenticationToken(
                                USER_EMAIL,
                                "unused"
                        )
                );

        when(userRepository.findByEmail(USER_EMAIL))
                .thenReturn(Optional.of(user));

        when(user.getId())
                .thenReturn(userId);

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(camera.getDepartment().getId())
                .thenReturn(departmentId);

        when(authorizationService.canAccessDepartment(userId, departmentId))
                .thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validSimplePolygonIsPersisted() {
        RestrictedZoneUpdateReq request =
                request(
                        new PointDto(0.10, 0.10),
                        new PointDto(0.90, 0.10),
                        new PointDto(0.90, 0.90),
                        new PointDto(0.10, 0.90)
                );

        when(restrictedZoneRepository.findByCameraIdAndActiveTrue(cameraId))
                .thenReturn(Optional.empty());

        restrictedZoneService.updateRestrictedZone(
                cameraId,
                request
        );

        ArgumentCaptor<RestrictedZone> captor =
                ArgumentCaptor.forClass(RestrictedZone.class);

        verify(restrictedZoneRepository)
                .save(captor.capture());

        RestrictedZone persisted =
                captor.getValue();

        assertThat(persisted.getCameraId())
                .isEqualTo(cameraId);

        assertThat(persisted.getName())
                .isEqualTo("Test Zone");

        assertThat(persisted.getPolygon())
                .containsExactlyElementsOf(request.getPolygon());

        assertThat(persisted.isActive())
                .isTrue();
    }

    @Test
    void selfIntersectingPolygonReturnsBadRequestAndIsNotPersisted() {
        RestrictedZoneUpdateReq request =
                request(
                        new PointDto(0.10, 0.10),
                        new PointDto(0.90, 0.90),
                        new PointDto(0.10, 0.90),
                        new PointDto(0.90, 0.10)
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> restrictedZoneService.updateRestrictedZone(
                                cameraId,
                                request
                        )
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        assertThat(exception.getReason())
                .isEqualTo("Poligon kendi kendini kesemez.");

        verify(restrictedZoneRepository, never())
                .findByCameraIdAndActiveTrue(cameraId);

        verify(restrictedZoneRepository, never())
                .save(any(RestrictedZone.class));
    }

    @Test
    void nonAdjacentEdgesTouchingEachOtherAreRejected() {
        RestrictedZoneUpdateReq request =
                request(
                        new PointDto(0.10, 0.10),
                        new PointDto(0.90, 0.10),
                        new PointDto(0.50, 0.50),
                        new PointDto(0.90, 0.90),
                        new PointDto(0.10, 0.90),
                        new PointDto(0.50, 0.50)
                );

        ResponseStatusException exception =
                assertThrows(
                        ResponseStatusException.class,
                        () -> restrictedZoneService.updateRestrictedZone(
                                cameraId,
                                request
                        )
                );

        assertThat(exception.getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verify(restrictedZoneRepository, never())
                .save(any(RestrictedZone.class));
    }

    private RestrictedZoneUpdateReq request(
            PointDto... points
    ) {
        RestrictedZoneUpdateReq request =
                new RestrictedZoneUpdateReq();

        request.setName("Test Zone");
        request.setPolygon(List.of(points));

        return request;
    }
}
