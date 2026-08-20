package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.domain.entity.CameraSession;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.camera.infrastructure.repository.CameraSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CameraConnectionMonitorTest {

    @Mock
    private CameraRepository cameraRepository;

    @Mock
    private CameraSessionRepository cameraSessionRepository;

    private CameraConnectionMonitor monitor;

    private final Instant now =
            Instant.parse("2026-08-20T00:00:00Z");

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);

        monitor = new CameraConnectionMonitor(
                cameraRepository,
                cameraSessionRepository,
                clock
        );
    }

    @Test
    void heartbeatWithinTwentySecondsKeepsCameraOnline() {
        Camera camera = cameraWithLastSeen(
                now.minusSeconds(10),
                Camera.Status.ONLINE
        );

        CameraSession session = activeSession(camera);

        when(cameraSessionRepository.findByStatus(
                CameraSession.SessionStatus.ACTIVE
        )).thenReturn(List.of(session));

        monitor.checkCameraConnections();

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.ONLINE);

        assertThat(session.getStatus())
                .isEqualTo(CameraSession.SessionStatus.ACTIVE);

        verify(cameraRepository, never()).save(any());
        verify(cameraSessionRepository, never()).save(any());
    }

    @Test
    void exactlyTwentySecondsKeepsCameraOnline() {
        Camera camera = cameraWithLastSeen(
                now.minusSeconds(20),
                Camera.Status.ONLINE
        );

        CameraSession session = activeSession(camera);

        when(cameraSessionRepository.findByStatus(
                CameraSession.SessionStatus.ACTIVE
        )).thenReturn(List.of(session));

        monitor.checkCameraConnections();

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.ONLINE);

        assertThat(session.getStatus())
                .isEqualTo(CameraSession.SessionStatus.ACTIVE);

        verify(cameraRepository, never()).save(any());
        verify(cameraSessionRepository, never()).save(any());
    }

    @Test
    void heartbeatOlderThanTwentySecondsMarksCameraWeak() {
        Camera camera = cameraWithLastSeen(
                now.minusSeconds(21),
                Camera.Status.ONLINE
        );

        CameraSession session = activeSession(camera);

        when(cameraSessionRepository.findByStatus(
                CameraSession.SessionStatus.ACTIVE
        )).thenReturn(List.of(session));

        monitor.checkCameraConnections();

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.WEAK);

        assertThat(session.getStatus())
                .isEqualTo(CameraSession.SessionStatus.ACTIVE);

        verify(cameraRepository).save(camera);
        verify(cameraSessionRepository, never()).save(any());
    }

    @Test
    void heartbeatOlderThanFortyFiveSecondsTimesOutSessionAndMarksCameraOffline() {
        Camera camera = cameraWithLastSeen(
                now.minusSeconds(46),
                Camera.Status.ONLINE
        );

        CameraSession session = activeSession(camera);

        when(cameraSessionRepository.findByStatus(
                CameraSession.SessionStatus.ACTIVE
        )).thenReturn(List.of(session));

        monitor.checkCameraConnections();

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.OFFLINE);

        assertThat(session.getStatus())
                .isEqualTo(CameraSession.SessionStatus.TIMED_OUT);

        assertThat(session.getEndedAt())
                .isEqualTo(now);

        verify(cameraRepository).save(camera);
        verify(cameraSessionRepository).save(session);
    }

    @Test
    void exactlyFortyFiveSecondsMarksCameraWeakWithoutTimingOutSession() {
        Camera camera = cameraWithLastSeen(
                now.minusSeconds(45),
                Camera.Status.ONLINE
        );

        CameraSession session = activeSession(camera);

        when(cameraSessionRepository.findByStatus(
                CameraSession.SessionStatus.ACTIVE
        )).thenReturn(List.of(session));

        monitor.checkCameraConnections();

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.WEAK);

        assertThat(session.getStatus())
                .isEqualTo(CameraSession.SessionStatus.ACTIVE);

        verify(cameraRepository).save(camera);
        verify(cameraSessionRepository, never()).save(any());
    }

    @Test
    void nullLastSeenDoesNotChangeCameraOrSession() {
        Camera camera = cameraWithLastSeen(
                null,
                Camera.Status.ONLINE
        );

        CameraSession session = activeSession(camera);

        when(cameraSessionRepository.findByStatus(
                CameraSession.SessionStatus.ACTIVE
        )).thenReturn(List.of(session));

        monitor.checkCameraConnections();

        assertThat(camera.getLastSeenAt())
                .isNull();

        assertThat(camera.getStatus())
                .isEqualTo(Camera.Status.ONLINE);

        assertThat(session.getStatus())
                .isEqualTo(CameraSession.SessionStatus.ACTIVE);

        assertThat(session.getEndedAt())
                .isNull();

        verify(cameraRepository, never())
                .save(any(Camera.class));

        verify(cameraSessionRepository, never())
                .save(any(CameraSession.class));
    }

    private Camera cameraWithLastSeen(
            Instant lastSeenAt,
            Camera.Status status
    ) {
        return Camera.builder()
                .id(UUID.randomUUID())
                .name("Camera 1")
                .code("CAM-001")
                .active(true)
                .status(status)
                .lastSeenAt(lastSeenAt)
                .build();
    }

    private CameraSession activeSession(Camera camera) {
        return CameraSession.builder()
                .id(UUID.randomUUID())
                .sessionId(UUID.randomUUID().toString())
                .camera(camera)
                .startedAt(now.minusSeconds(60))
                .status(CameraSession.SessionStatus.ACTIVE)
                .build();
    }
}