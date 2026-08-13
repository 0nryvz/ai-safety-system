package com.isg.backend.camera.service;

import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.domain.entity.CameraSession;
import com.isg.backend.modules.camera.infrastructure.repository.CameraSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseCameraQueryServiceTest {

    private CameraSessionRepository cameraSessionRepository;
    private DatabaseCameraQueryService service;

    @BeforeEach
    void setUp() {
        cameraSessionRepository =
                mock(CameraSessionRepository.class);

        service =
                new DatabaseCameraQueryService(
                        cameraSessionRepository
                );
    }

    @Test
    void acceptsActiveSessionBelongingToCamera() {
        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Camera camera = mock(Camera.class);
        when(camera.getId()).thenReturn(cameraId);

        CameraSession session = mock(CameraSession.class);
        when(session.getCamera()).thenReturn(camera);
        when(session.getStatus())
                .thenReturn(CameraSession.SessionStatus.ACTIVE);

        when(cameraSessionRepository.findBySessionId(
                sessionId.toString()
        )).thenReturn(Optional.of(session));

        assertTrue(
                service.isValid(
                        cameraId,
                        sessionId
                )
        );
    }

    @Test
    void rejectsSessionBelongingToDifferentCamera() {
        UUID cameraId = UUID.randomUUID();
        UUID differentCameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Camera camera = mock(Camera.class);
        when(camera.getId()).thenReturn(differentCameraId);

        CameraSession session = mock(CameraSession.class);
        when(session.getCamera()).thenReturn(camera);
        when(session.getStatus())
                .thenReturn(CameraSession.SessionStatus.ACTIVE);

        when(cameraSessionRepository.findBySessionId(
                sessionId.toString()
        )).thenReturn(Optional.of(session));

        assertFalse(
                service.isValid(
                        cameraId,
                        sessionId
                )
        );
    }

    @Test
    void rejectsClosedSession() {
        assertInactiveSessionIsRejected(
                CameraSession.SessionStatus.CLOSED
        );
    }

    @Test
    void rejectsTimedOutSession() {
        assertInactiveSessionIsRejected(
                CameraSession.SessionStatus.TIMED_OUT
        );
    }

    private void assertInactiveSessionIsRejected(
            CameraSession.SessionStatus status
    ) {
        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        Camera camera = mock(Camera.class);
        when(camera.getId()).thenReturn(cameraId);

        CameraSession session = mock(CameraSession.class);
        when(session.getCamera()).thenReturn(camera);
        when(session.getStatus()).thenReturn(status);

        when(cameraSessionRepository.findBySessionId(
                sessionId.toString()
        )).thenReturn(Optional.of(session));

        assertFalse(
                service.isValid(
                        cameraId,
                        sessionId
                )
        );
    }
}