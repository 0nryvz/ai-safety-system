package com.isg.backend.recording.infrastructure.violation;

import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import com.isg.backend.violation.application.port.RecordingQueryPort;
import com.isg.backend.violation.application.port.RecordingQueryResult;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
public class RecordingQueryBridgeAdapter
        implements RecordingQueryPort {

    private final RecordingRepository recordingRepository;

    public RecordingQueryBridgeAdapter(
            RecordingRepository recordingRepository
    ) {
        this.recordingRepository =
                recordingRepository;
    }

    @Override
    public Optional<RecordingQueryResult> findByViolationId(
            UUID violationId
    ) {
        return recordingRepository
                .findByViolationId(
                        violationId
                )
                .map(
                        this::toQueryResult
                );
    }

    @Override
    public Map<UUID, RecordingQueryResult> findByViolationIds(
            Collection<UUID> violationIds
    ) {
        return recordingRepository
                .findByViolationIds(
                        violationIds
                )
                .entrySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> toQueryResult(
                                        entry.getValue()
                                )
                        )
                );
    }

    @Override
    public Set<UUID> findViolationIdsByRecordingStatus(
            String recordingStatus
    ) {
        RecordingStatus status =
                RecordingStatus.valueOf(
                        recordingStatus
                );

        return recordingRepository
                .findViolationIdsByStatus(
                        status
                );
    }

    private RecordingQueryResult toQueryResult(
            Recording recording
    ) {
        boolean clipReady =
                recording.status()
                        == RecordingStatus.READY
                        && recording.objectKey() != null
                        && !recording.objectKey().isBlank();

        return new RecordingQueryResult(
                recording.status().name(),
                clipReady,
                null
        );
    }
}