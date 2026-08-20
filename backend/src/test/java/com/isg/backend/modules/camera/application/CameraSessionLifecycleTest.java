package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.api.dto.CameraSessionRequest;
import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.domain.entity.CameraSession;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.camera.infrastructure.repository.CameraSessionRepository;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import com.isg.backend.modules.user.service.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CameraSessionLifecycleTest {

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

    private final Instant now =
            Instant.parse("2026-08-20T00:00:00Z");

    private UUID cameraId;
    private UUID sessionUuid;
    private Camera camera;

    @BeforeEach
    void setUp() {
        Clock clock =
                Clock.fixed(now, ZoneOffset.UTC);

        cameraService = new CameraService(
                cameraRepository,
                departmentRepository,
                cameraSessionRepository,
                authorizationService,
                userRepository,
                clock
        );

        cameraId = UUID.randomUUID();
        sessionUuid = UUID.randomUUID();

        camera = Camera.builder()
                .id(cameraId)
                .name("Camera 1")
                .code("CAM-001")
                .active(true)
                .status(Camera.Status.OFFLINE)
                .build();
    }

    @Test
    void freshCameraOpenCreatesActiveSessionAndMarksCameraOnline() {
        CameraSessionRequest request =
                request(cameraId, sessionUuid);

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.empty());

        when(cameraSessionRepository.findByCameraIdAndStatus(
                cameraId,
                CameraSession.SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        cameraService.openSession(request);

        verify(cameraSessionRepository).save(
                argThat(session ->
                        session.getSessionId()
                                .equals(sessionUuid.toString())
                                && session.getCamera() == camera
                                && session.getStatus()
                                == CameraSession.SessionStatus.ACTIVE
                                && now.equals(session.getStartedAt())
                )
        );

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.ONLINE);

        assertThat(camera.getLastSeenAt())
                .isEqualTo(now);

        verify(cameraRepository).save(camera);
    }

    @Test
    void duplicateOpenWithSameActiveSessionIsIdempotent() {
        CameraSessionRequest request =
                request(cameraId, sessionUuid);

        CameraSession existing =
                activeSession(camera, sessionUuid);

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(existing));

        cameraService.openSession(request);

        verify(cameraSessionRepository, never())
                .save(any(CameraSession.class));

        verify(cameraSessionRepository, never())
                .findByCameraIdAndStatus(
                        any(),
                        any()
                );

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.ONLINE);

        assertThat(camera.getLastSeenAt())
                .isEqualTo(now);

        verify(cameraRepository).save(camera);
    }

    @Test
    void secondDifferentActiveSessionForSameCameraReturnsConflict() {
        UUID oldSessionId = UUID.randomUUID();

        CameraSessionRequest request =
                request(cameraId, sessionUuid);

        CameraSession activeSession =
                activeSession(camera, oldSessionId);

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.empty());

        when(cameraSessionRepository.findByCameraIdAndStatus(
                cameraId,
                CameraSession.SessionStatus.ACTIVE))
                .thenReturn(Optional.of(activeSession));

        assertThatThrownBy(
                () -> cameraService.openSession(request)
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT)
                );

        verify(cameraSessionRepository, never())
                .save(any(CameraSession.class));
    }

    @Test
    void sameSessionIdBelongingToDifferentCameraReturnsConflict() {
        UUID otherCameraId = UUID.randomUUID();

        Camera otherCamera = Camera.builder()
                .id(otherCameraId)
                .active(true)
                .status(Camera.Status.ONLINE)
                .build();

        CameraSession existing =
                activeSession(otherCamera, sessionUuid);

        CameraSessionRequest request =
                request(cameraId, sessionUuid);

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(existing));

        assertThatThrownBy(
                () -> cameraService.openSession(request)
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT)
                );
    }

    @Test
    void closedSessionIdCannotBeReusedForOpen() {
        CameraSessionRequest request =
                request(cameraId, sessionUuid);

        CameraSession closed =
                activeSession(camera, sessionUuid);

        closed.setStatus(
                CameraSession.SessionStatus.CLOSED
        );

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(closed));

        assertThatThrownBy(
                () -> cameraService.openSession(request)
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT)
                );
    }

    @Test
    void timedOutSessionIdCannotBeReusedForOpen() {
        CameraSessionRequest request =
                request(cameraId, sessionUuid);

        CameraSession timedOut =
                activeSession(camera, sessionUuid);

        timedOut.setStatus(
                CameraSession.SessionStatus.TIMED_OUT
        );

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(timedOut));

        assertThatThrownBy(
                () -> cameraService.openSession(request)
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT)
                );

        verify(cameraSessionRepository, never())
                .save(any(CameraSession.class));

        verify(cameraRepository, never())
                .save(any(Camera.class));
    }

    @Test
    void heartbeatUpdatesLastSeenAndKeepsCameraOnline() {
        CameraSession session =
                activeSession(camera, sessionUuid);

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(session));

        cameraService.processHeartbeat(
                cameraId,
                sessionUuid.toString()
        );

        assertThat(camera.getLastSeenAt())
                .isEqualTo(now);

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.ONLINE);

        verify(cameraRepository).save(camera);
    }

    @Test
    void heartbeatWithMismatchedCameraIdReturnsConflict() {
        CameraSession session =
                activeSession(camera, sessionUuid);

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(
                () -> cameraService.processHeartbeat(
                        UUID.randomUUID(),
                        sessionUuid.toString()
                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT)
                );

        verify(cameraRepository, never())
                .save(any(Camera.class));
    }

    @Test
    void heartbeatForUnknownSessionReturnsNotFound() {
        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> cameraService.processHeartbeat(
                        cameraId,
                        sessionUuid.toString()
                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND)
                );

        verify(cameraRepository, never())
                .save(any(Camera.class));
    }

    @Test
    void heartbeatForTimedOutSessionReturnsConflict() {
        CameraSession session =
                activeSession(camera, sessionUuid);

        session.setStatus(
                CameraSession.SessionStatus.TIMED_OUT
        );

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(
                () -> cameraService.processHeartbeat(
                        cameraId,
                        sessionUuid.toString()
                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT)
                );

        verify(cameraRepository, never())
                .save(any(Camera.class));
    }

    @Test
    void heartbeatForInactiveCameraReturnsConflict() {
        camera.setActive(false);

        CameraSession session =
                activeSession(camera, sessionUuid);

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(
                () -> cameraService.processHeartbeat(
                        cameraId,
                        sessionUuid.toString()
                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT)
                );

        verify(cameraRepository, never())
                .save(any(Camera.class));
    }

    @Test
    void closeActiveSessionMarksSessionClosedAndCameraOffline() {
        CameraSession session =
                activeSession(camera, sessionUuid);

        camera.setStatus(Camera.Status.ONLINE);

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(session));

        cameraService.closeSession(
                cameraId,
                sessionUuid.toString()
        );

        assertThat(session.getStatus())
                .isEqualTo(
                        CameraSession.SessionStatus.CLOSED
                );

        assertThat(session.getEndedAt())
                .isEqualTo(now);

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.OFFLINE);

        verify(cameraSessionRepository).save(session);
        verify(cameraRepository).save(camera);
    }

    @Test
    void duplicateCloseOfClosedSessionIsIdempotent() {
        CameraSession session =
                activeSession(camera, sessionUuid);

        session.setStatus(
                CameraSession.SessionStatus.CLOSED
        );

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(session));

        cameraService.closeSession(
                cameraId,
                sessionUuid.toString()
        );

        verify(cameraSessionRepository, never())
                .save(any(CameraSession.class));

        verify(cameraRepository, never())
                .save(any(Camera.class));
    }

    @Test
    void closeUnknownSessionReturnsNotFound() {
        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> cameraService.closeSession(
                        cameraId,
                        sessionUuid.toString()
                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.NOT_FOUND)
                );

        verify(cameraSessionRepository, never())
                .save(any(CameraSession.class));

        verify(cameraRepository, never())
                .save(any(Camera.class));
    }

    @Test
    void closeWithMismatchedCameraIdReturnsConflict() {
        CameraSession session =
                activeSession(camera, sessionUuid);

        when(cameraSessionRepository.findBySessionId(
                sessionUuid.toString()))
                .thenReturn(Optional.of(session));

        assertThatThrownBy(
                () -> cameraService.closeSession(
                        UUID.randomUUID(),
                        sessionUuid.toString()
                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT)
                );

        verify(cameraSessionRepository, never())
                .save(any(CameraSession.class));

        verify(cameraRepository, never())
                .save(any(Camera.class));
    }

    @Test
    void inactiveCameraCannotOpenSession() {
        camera.setActive(false);

        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        assertThatThrownBy(
                () -> cameraService.openSession(
                        request(cameraId, sessionUuid)
                )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode())
                                .isEqualTo(HttpStatus.CONFLICT)
                );

        verify(cameraSessionRepository, never())
                .save(any(CameraSession.class));
    }

    @Test
    void newSessionCanOpenAfterPreviousSessionTimedOut() {
        UUID newSessionId = UUID.randomUUID();

        CameraSessionRequest request =
                request(cameraId, newSessionId);

        /*
         * Önceki session TIMED_OUT olduğu için artık ACTIVE session yoktur.
         * Yeni Start işlemi yeni bir sessionId ile açılabilmelidir.
         */
        when(cameraRepository.findById(cameraId))
                .thenReturn(Optional.of(camera));

        when(cameraSessionRepository.findBySessionId(
                newSessionId.toString()))
                .thenReturn(Optional.empty());

        when(cameraSessionRepository.findByCameraIdAndStatus(
                cameraId,
                CameraSession.SessionStatus.ACTIVE))
                .thenReturn(Optional.empty());

        cameraService.openSession(request);

        verify(cameraSessionRepository).save(
                argThat(session ->
                        session.getSessionId()
                                .equals(newSessionId.toString())
                                && session.getCamera() == camera
                                && session.getStatus()
                                == CameraSession.SessionStatus.ACTIVE
                                && now.equals(session.getStartedAt())
                )
        );

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.ONLINE);

        assertThat(camera.getLastSeenAt())
                .isEqualTo(now);

        verify(cameraRepository).save(camera);
    }

    @Test
    void lateCloseForTimedOutSessionDoesNotMarkCameraOffline() {
        UUID oldSessionId = UUID.randomUUID();

        CameraSession oldSession =
                activeSession(camera, oldSessionId);

        oldSession.setStatus(
                CameraSession.SessionStatus.TIMED_OUT
        );

        oldSession.setEndedAt(
                now.minusSeconds(5)
        );

        /*
         * Eski session timeout olduktan sonra kamera yeni bir session
         * nedeniyle tekrar ONLINE olmuş olabilir.
         */
        camera.setStatus(Camera.Status.ONLINE);
        camera.setLastSeenAt(now);

        when(cameraSessionRepository.findBySessionId(
                oldSessionId.toString()))
                .thenReturn(Optional.of(oldSession));

        /*
         * Eski TIMED_OUT session için geç gelen close isteği
         * yeni session'ın kamera durumunu bozmamalıdır.
         */
        cameraService.closeSession(
                cameraId,
                oldSessionId.toString()
        );

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.ONLINE);

        assertThat(oldSession.getStatus())
                .isEqualTo(CameraSession.SessionStatus.TIMED_OUT);

        verify(cameraSessionRepository, never())
                .save(any(CameraSession.class));

        verify(cameraRepository, never())
                .save(any(Camera.class));
    }

    private CameraSessionRequest request(
            UUID cameraId,
            UUID sessionId
    ) {
        CameraSessionRequest request =
                new CameraSessionRequest();

        request.setCameraId(cameraId);
        request.setSessionId(sessionId.toString());
        request.setDeviceInfo("flutter-test-device");

        return request;
    }

    private CameraSession activeSession(
            Camera owner,
            UUID sessionId
    ) {
        return CameraSession.builder()
                .id(UUID.randomUUID())
                .camera(owner)
                .sessionId(sessionId.toString())
                .clientInfo("flutter-test-device")
                .startedAt(now.minusSeconds(10))
                .status(CameraSession.SessionStatus.ACTIVE)
                .build();
    }
}