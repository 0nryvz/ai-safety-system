package com.isg.backend.recording.application;

import com.isg.backend.recording.application.port.PlaybackUrlPort;
import com.isg.backend.recording.application.port.PresignedPlaybackUrl;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class RecordingMediaAccessService {

    private final RecordingRepository recordingRepository;
    private final PlaybackUrlPort playbackUrlPort;

    public RecordingMediaAccessService(
            RecordingRepository recordingRepository,
            PlaybackUrlPort playbackUrlPort
    ) {
        this.recordingRepository =
                Objects.requireNonNull(
                        recordingRepository
                );

        this.playbackUrlPort =
                Objects.requireNonNull(
                        playbackUrlPort
                );
    }

    public PresignedPlaybackUrl createClipUrl(
            UUID violationId
    ) {
        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        Recording recording =
                recordingRepository
                        .findByViolationId(
                                violationId
                        )
                        .orElseThrow(
                                () ->
                                        new RecordingNotFoundForViolationException(
                                                violationId
                                        )
                        );

        if (recording.status()
                != RecordingStatus.READY) {
            throw new RecordingNotReadyException(
                    violationId,
                    recording.status()
            );
        }

        String objectKey =
                recording.objectKey();

        if (objectKey == null
                || objectKey.isBlank()) {
            throw new RecordingNotReadyException(
                    violationId,
                    recording.status()
            );
        }

        return playbackUrlPort.createGetUrl(
                objectKey
        );
    }
}