package com.isg.backend.camera.service;

import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.domain.entity.CameraSession;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.camera.infrastructure.repository.CameraSessionRepository;
import com.isg.backend.modules.user.entity.Department;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DatabaseCameraQueryServiceTest {

    private CameraSessionRepository cameraSessionRepository;
    private CameraRepository cameraRepository;
    private DatabaseCameraQueryService service;

    @BeforeEach
    void setUp() {
        cameraSessionRepository =
                mock(CameraSessionRepository.class);

        cameraRepository =
                mock(CameraRepository.class);

        service =
                new DatabaseCameraQueryService(
                        cameraSessionRepository,
                        cameraRepository
                );
    }

    @Test
    void resolvesInternalSessionRecordIdForActiveMatchingSession() {
        UUID cameraId =
                UUID.randomUUID();

        UUID externalSessionId =
                UUID.randomUUID();

        UUID internalSessionRecordId =
                UUID.randomUUID();

        Camera camera =
                mock(Camera.class);

        when(camera.getId())
                .thenReturn(
                        cameraId
                );

        CameraSession session =
                mock(CameraSession.class);

        when(session.getId())
                .thenReturn(
                        internalSessionRecordId
                );

        when(session.getCamera())
                .thenReturn(
                        camera
                );

        when(session.getStatus())
                .thenReturn(
                        CameraSession.SessionStatus.ACTIVE
                );

        when(
                cameraSessionRepository.findBySessionId(
                        externalSessionId.toString()
                )
        ).thenReturn(
                Optional.of(
                        session
                )
        );

        assertTrue(
                service.findSessionRecordId(
                        cameraId,
                        externalSessionId
                ).isPresent()
        );

        assertEquals(
                internalSessionRecordId,
                service.findSessionRecordId(
                        cameraId,
                        externalSessionId
                ).orElseThrow()
        );
    }

    @Test
    void doesNotResolveSessionRecordIdForDifferentCamera() {
        UUID cameraId =
                UUID.randomUUID();

        UUID differentCameraId =
                UUID.randomUUID();

        UUID externalSessionId =
                UUID.randomUUID();

        Camera camera =
                mock(Camera.class);

        when(camera.getId())
                .thenReturn(
                        differentCameraId
                );

        CameraSession session =
                mock(CameraSession.class);

        when(session.getCamera())
                .thenReturn(
                        camera
                );

        when(session.getStatus())
                .thenReturn(
                        CameraSession.SessionStatus.ACTIVE
                );

        when(
                cameraSessionRepository.findBySessionId(
                        externalSessionId.toString()
                )
        ).thenReturn(
                Optional.of(
                        session
                )
        );

        assertTrue(
                service.findSessionRecordId(
                        cameraId,
                        externalSessionId
                ).isEmpty()
        );
    }

    @Test
    void doesNotResolveSessionRecordIdForInactiveSession() {
        UUID cameraId =
                UUID.randomUUID();

        UUID externalSessionId =
                UUID.randomUUID();

        Camera camera =
                mock(Camera.class);

        when(camera.getId())
                .thenReturn(
                        cameraId
                );

        CameraSession session =
                mock(CameraSession.class);

        when(session.getCamera())
                .thenReturn(
                        camera
                );

        when(session.getStatus())
                .thenReturn(
                        CameraSession.SessionStatus.CLOSED
                );

        when(
                cameraSessionRepository.findBySessionId(
                        externalSessionId.toString()
                )
        ).thenReturn(
                Optional.of(
                        session
                )
        );

        assertTrue(
                service.findSessionRecordId(
                        cameraId,
                        externalSessionId
                ).isEmpty()
        );
    }

    @Test
    void acceptsActiveSessionBelongingToCamera() {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Camera camera =
                mock(Camera.class);

        when(camera.getId())
                .thenReturn(
                        cameraId
                );

        CameraSession session =
                mock(CameraSession.class);

        when(session.getCamera())
                .thenReturn(
                        camera
                );

        when(session.getStatus())
                .thenReturn(
                        CameraSession.SessionStatus.ACTIVE
                );

        when(
                cameraSessionRepository.findBySessionId(
                        sessionId.toString()
                )
        ).thenReturn(
                Optional.of(
                        session
                )
        );

        assertTrue(
                service.isValid(
                        cameraId,
                        sessionId
                )
        );
    }

    @Test
    void rejectsSessionBelongingToDifferentCamera() {
        UUID cameraId =
                UUID.randomUUID();

        UUID differentCameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Camera camera =
                mock(Camera.class);

        when(camera.getId())
                .thenReturn(
                        differentCameraId
                );

        CameraSession session =
                mock(CameraSession.class);

        when(session.getCamera())
                .thenReturn(
                        camera
                );

        when(session.getStatus())
                .thenReturn(
                        CameraSession.SessionStatus.ACTIVE
                );

        when(
                cameraSessionRepository.findBySessionId(
                        sessionId.toString()
                )
        ).thenReturn(
                Optional.of(
                        session
                )
        );

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

    @Test
    void resolvesDepartmentIdForCamera() {
        UUID cameraId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        Department department =
                mock(Department.class);

        when(department.getId())
                .thenReturn(
                        departmentId
                );

        Camera camera =
                mock(Camera.class);

        when(camera.getDepartment())
                .thenReturn(
                        department
                );

        when(cameraRepository.findById(
                cameraId
        )).thenReturn(
                Optional.of(
                        camera
                )
        );

        assertEquals(
                departmentId,
                service.findDepartmentId(
                        cameraId
                ).orElseThrow()
        );
    }

    @Test
    void doesNotResolveDepartmentIdWhenCameraDoesNotExist() {
        UUID cameraId =
                UUID.randomUUID();

        when(cameraRepository.findById(
                cameraId
        )).thenReturn(
                Optional.empty()
        );

        assertTrue(
                service.findDepartmentId(
                        cameraId
                ).isEmpty()
        );
    }

    @Test
    void doesNotResolveDepartmentIdWhenCameraHasNoDepartment() {
        UUID cameraId =
                UUID.randomUUID();

        Camera camera =
                mock(Camera.class);

        when(camera.getDepartment())
                .thenReturn(
                        null
                );

        when(cameraRepository.findById(
                cameraId
        )).thenReturn(
                Optional.of(
                        camera
                )
        );

        assertTrue(
                service.findDepartmentId(
                        cameraId
                ).isEmpty()
        );
    }

    private void assertInactiveSessionIsRejected(
            CameraSession.SessionStatus status
    ) {
        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Camera camera =
                mock(Camera.class);

        when(camera.getId())
                .thenReturn(
                        cameraId
                );

        CameraSession session =
                mock(CameraSession.class);

        when(session.getCamera())
                .thenReturn(
                        camera
                );

        when(session.getStatus())
                .thenReturn(
                        status
                );

        when(
                cameraSessionRepository.findBySessionId(
                        sessionId.toString()
                )
        ).thenReturn(
                Optional.of(
                        session
                )
        );

        assertFalse(
                service.isValid(
                        cameraId,
                        sessionId
                )
        );
    }
}