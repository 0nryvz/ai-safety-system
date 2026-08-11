package com.isg.backend.recording.application;

import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class RecordingCreationService {

    private final RecordingRepository recordingRepository;

    public RecordingCreationService(
            RecordingRepository recordingRepository
    ) {
        this.recordingRepository = Objects.requireNonNull(
                recordingRepository,
                "recordingRepository cannot be null"
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recording createRequested(
            UUID violationId,
            UUID startCommandId
    ) {
        Recording recording = Recording.createRequested(
                violationId,
                startCommandId
        );

        return recordingRepository.save(recording);
    }
}