package com.isg.backend.recording.infrastructure.violation;

import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import com.isg.backend.violation.application.port.RecordingQueryPort;
import com.isg.backend.violation.application.port.RecordingQueryResult;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

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