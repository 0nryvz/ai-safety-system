package com.isg.backend.recording.application.port;

import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import java.util.List;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RecordingRepository {

    Optional<Recording> findById(
            UUID recordingId
    );

    Optional<Recording> findByViolationId(
            UUID violationId
    );

    Map<UUID, Recording> findByViolationIds(
            Collection<UUID> violationIds
    );

    List<Recording> findByClipGroupId(
            UUID clipGroupId
    );

    Set<UUID> findViolationIdsByStatus(
            RecordingStatus status
    );

    Recording save(
            Recording recording
    );
}