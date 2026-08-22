package com.isg.backend.recording.application;

import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class RecordingCommandStateService {

    private final RecordingRepository recordingRepository;

    public RecordingCommandStateService(
            RecordingRepository recordingRepository
    ) {
        this.recordingRepository = Objects.requireNonNull(
                recordingRepository,
                "recordingRepository cannot be null"
        );
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recording claimStopCommand(
            UUID recordingId,
            UUID proposedStopCommandId
    ) {
        Objects.requireNonNull(
                proposedStopCommandId,
                "proposedStopCommandId cannot be null"
        );

        Recording recording =
                recordingRepository.findById(recordingId)
                        .orElseThrow(
                                () -> new RecordingNotFoundException(
                                        recordingId
                                )
                        );

        if (recording.stopCommandId() != null) {
            return recording;
        }

        recording.assignStopCommandId(proposedStopCommandId);
        return recordingRepository.save(recording);
    }
}
