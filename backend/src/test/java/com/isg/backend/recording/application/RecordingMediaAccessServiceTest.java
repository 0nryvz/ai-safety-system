package com.isg.backend.recording.application;

import com.isg.backend.recording.application.port.PlaybackUrlPort;
import com.isg.backend.recording.application.port.PresignedPlaybackUrl;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RecordingMediaAccessServiceTest {

    private RecordingRepository recordingRepository;
    private PlaybackUrlPort playbackUrlPort;
    private RecordingMediaAccessService service;

    @BeforeEach
    void setUp() {
        recordingRepository =
                mock(RecordingRepository.class);

        playbackUrlPort =
                mock(PlaybackUrlPort.class);

        service =
                new RecordingMediaAccessService(
                        recordingRepository,
                        playbackUrlPort
                );
    }

    @Test
    void rejectsWhenRecordingDoesNotExist() {
        UUID violationId =
                UUID.randomUUID();

        when(
                recordingRepository.findByViolationId(
                        violationId
                )
        ).thenReturn(
                Optional.empty()
        );

        assertThatThrownBy(
                () ->
                        service.createClipUrl(
                                violationId
                        )
        ).isInstanceOf(
                RecordingNotFoundForViolationException.class
        );

        verifyNoInteractions(
                playbackUrlPort
        );
    }

    @Test
    void rejectsWhenRecordingIsNotReady() {
        UUID violationId =
                UUID.randomUUID();

        Recording recording =
                Recording.rehydrate(
                        UUID.randomUUID(),
                        violationId,
                        RecordingStatus.PROCESSING,
                        Instant.parse(
                                "2026-08-18T03:00:00Z"
                        ),
                        UUID.randomUUID(),
                        UUID.randomUUID()
                );

        when(
                recordingRepository.findByViolationId(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        recording
                )
        );

        assertThatThrownBy(
                () ->
                        service.createClipUrl(
                                violationId
                        )
        ).isInstanceOf(
                RecordingNotReadyException.class
        );

        verifyNoInteractions(
                playbackUrlPort
        );
    }

    @Test
    void rejectsReadyRecordingWithoutObjectKey() {
        UUID violationId =
                UUID.randomUUID();

        Recording recording =
                Recording.rehydrate(
                        UUID.randomUUID(),
                        violationId,
                        RecordingStatus.READY,
                        null,
                        30_000,
                        700_000L,
                        0,
                        "sha256:test",
                        null,
                        Instant.parse(
                                "2026-08-18T03:00:00Z"
                        ),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse(
                                "2026-08-18T03:00:30Z"
                        )
                );

        when(
                recordingRepository.findByViolationId(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        recording
                )
        );

        assertThatThrownBy(
                () ->
                        service.createClipUrl(
                                violationId
                        )
        ).isInstanceOf(
                RecordingNotReadyException.class
        );

        verifyNoInteractions(
                playbackUrlPort
        );
    }

    @Test
    void createsPlaybackUrlFromDatabaseObjectKey() {
        UUID violationId =
                UUID.randomUUID();

        String objectKey =
                "violations/2026/08/test/recording.mp4";

        Recording recording =
                Recording.rehydrate(
                        UUID.randomUUID(),
                        violationId,
                        RecordingStatus.READY,
                        objectKey,
                        30_000,
                        700_000L,
                        0,
                        "sha256:test",
                        null,
                        Instant.parse(
                                "2026-08-18T03:00:00Z"
                        ),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.parse(
                                "2026-08-18T03:00:30Z"
                        )
                );

        PresignedPlaybackUrl expected =
                new PresignedPlaybackUrl(
                        "http://localhost:9000/presigned",
                        Instant.parse(
                                "2026-08-18T03:05:00Z"
                        )
                );

        when(
                recordingRepository.findByViolationId(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        recording
                )
        );

        when(
                playbackUrlPort.createGetUrl(
                        objectKey
                )
        ).thenReturn(
                expected
        );

        PresignedPlaybackUrl result =
                service.createClipUrl(
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
                objectKey
        );
    }
}