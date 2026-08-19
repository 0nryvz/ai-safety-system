package com.isg.backend.recording.application.port;

import com.isg.backend.recording.domain.Recording;

import java.util.Optional;
import java.util.UUID;

public interface RecordingRepository {
    Optional<Recording> findById(UUID recordingId);

    Optional<Recording> findByViolationId(UUID violationId);

    Recording save(Recording recording);
}
