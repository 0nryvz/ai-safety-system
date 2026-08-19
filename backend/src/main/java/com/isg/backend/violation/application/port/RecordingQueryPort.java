package com.isg.backend.violation.application.port;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RecordingQueryPort {

    Optional<RecordingQueryResult> findByViolationId(
            UUID violationId
    );

    Map<UUID, RecordingQueryResult> findByViolationIds(
            Collection<UUID> violationIds
    );

    Set<UUID> findViolationIdsByRecordingStatus(
            String recordingStatus
    );
}