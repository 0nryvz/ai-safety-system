package com.isg.backend.violation.service;

import com.isg.backend.modules.user.service.AuthorizationService;
import com.isg.backend.recording.application.RecordingMediaAccessService;
import com.isg.backend.recording.application.port.PresignedPlaybackUrl;
import com.isg.backend.violation.exception.ViolationNotFoundException;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import com.isg.backend.recording.application.port.PlaybackUrlPort;
import com.isg.backend.violation.exception.CoverImageNotReadyException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ViolationMediaAccessServiceTest {

    private SpringDataViolationRepository violationRepository;
    private AuthorizationService authorizationService;
    private RecordingMediaAccessService recordingMediaAccessService;
    private PlaybackUrlPort playbackUrlPort;
    private ViolationMediaAccessService service;


    @BeforeEach
    void setUp() {
        violationRepository =
                mock(SpringDataViolationRepository.class);

        authorizationService =
                mock(AuthorizationService.class);

        recordingMediaAccessService =
                mock(RecordingMediaAccessService.class);

        playbackUrlPort =
                mock(PlaybackUrlPort.class);

        service =
                new ViolationMediaAccessService(
                        violationRepository,
                        authorizationService,
                        recordingMediaAccessService,
                        playbackUrlPort
                );

    }

    @Test
    void rejectsWhenViolationDoesNotExist() {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        when(
                violationRepository.findById(
                        violationId
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () ->
                        service.createClipUrl(
                                userId,
                                violationId
                        )
        ).isInstanceOf(
                ViolationNotFoundException.class
        );

        verifyNoInteractions(
                authorizationService,
                recordingMediaAccessService
        );
    }

    @Test
    void rejectsWhenUserCannotAccessViolationDepartment() {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(
                violation.getDepartmentId()
        ).thenReturn(
                departmentId
        );

        when(
                violationRepository.findById(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(
                authorizationService.canAccessDepartment(
                        userId,
                        departmentId
                )
        ).thenReturn(
                false
        );

        assertThatThrownBy(
                () ->
                        service.createClipUrl(
                                userId,
                                violationId
                        )
        )
                .isInstanceOf(
                        ResponseStatusException.class
                )
                .satisfies(
                        throwable -> {
                            ResponseStatusException exception =
                                    (ResponseStatusException) throwable;

                            assertThat(
                                    exception.getStatusCode()
                            ).isEqualTo(
                                    HttpStatus.FORBIDDEN
                            );
                        }
                );

        verifyNoInteractions(
                recordingMediaAccessService
        );
    }

    @Test
    void returnsClipUrlWhenUserCanAccessViolationDepartment() {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(
                violation.getDepartmentId()
        ).thenReturn(
                departmentId
        );

        when(
                violationRepository.findById(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(
                authorizationService.canAccessDepartment(
                        userId,
                        departmentId
                )
        ).thenReturn(
                true
        );

        PresignedPlaybackUrl expected =
                new PresignedPlaybackUrl(
                        "http://localhost:9000/presigned",
                        Instant.parse(
                                "2026-08-18T18:00:00Z"
                        )
                );

        when(
                recordingMediaAccessService.createClipUrl(
                        violationId
                )
        ).thenReturn(
                expected
        );

        PresignedPlaybackUrl result =
                service.createClipUrl(
                        userId,
                        violationId
                );

        assertThat(
                result
        ).isEqualTo(
                expected
        );

        verify(
                authorizationService
        ).canAccessDepartment(
                userId,
                departmentId
        );

        verify(
                recordingMediaAccessService
        ).createClipUrl(
                violationId
        );
    }

    @Test
    void returnsCoverUrlWhenCoverImageIsReady() {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        String coverImageKey =
                "violations/2026/08/"
                        + violationId
                        + "/cover.jpg";

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(
                violation.getDepartmentId()
        ).thenReturn(
                departmentId
        );

        when(
                violation.getCoverImageKey()
        ).thenReturn(
                coverImageKey
        );

        when(
                violationRepository.findById(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(
                authorizationService.canAccessDepartment(
                        userId,
                        departmentId
                )
        ).thenReturn(
                true
        );

        PresignedPlaybackUrl expected =
                new PresignedPlaybackUrl(
                        "http://localhost:9000/cover-presigned",
                        Instant.parse(
                                "2026-08-18T18:05:00Z"
                        )
                );

        when(
                playbackUrlPort.createGetUrl(
                        coverImageKey
                )
        ).thenReturn(
                expected
        );

        PresignedPlaybackUrl result =
                service.createCoverUrl(
                        userId,
                        violationId
                );

        assertThat(
                result
        ).isEqualTo(
                expected
        );

        verify(
                playbackUrlPort
        ).createGetUrl(
                coverImageKey
        );
    }

    @Test
    void rejectsCoverUrlWhenCoverImageIsNotReady() {
        UUID userId =
                UUID.randomUUID();

        UUID violationId =
                UUID.randomUUID();

        UUID departmentId =
                UUID.randomUUID();

        ViolationJpaEntity violation =
                mock(ViolationJpaEntity.class);

        when(
                violation.getDepartmentId()
        ).thenReturn(
                departmentId
        );

        when(
                violation.getCoverImageKey()
        ).thenReturn(
                null
        );

        when(
                violationRepository.findById(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        violation
                )
        );

        when(
                authorizationService.canAccessDepartment(
                        userId,
                        departmentId
                )
        ).thenReturn(
                true
        );

        assertThatThrownBy(
                () ->
                        service.createCoverUrl(
                                userId,
                                violationId
                        )
        ).isInstanceOf(
                CoverImageNotReadyException.class
        );

        verifyNoInteractions(
                playbackUrlPort
        );
    }
}