package com.isg.backend.recording.application;

import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordingApplicationService {

    private final RecordingRepository recordingRepository;

    public RecordingApplicationService(
            RecordingRepository recordingRepository
    ) {
        this.recordingRepository = recordingRepository;
    }

    @Transactional
    public Recording start(
            StartRecordingCommand command
    ) {
        return recordingRepository.findByViolationId(command.violationId())
                .orElseGet(() -> {
                    Recording recording = Recording.createRequested(command.violationId());
                    return recordingRepository.save(recording);
                });
    }

    @Transactional
    public Recording stop(
            StopRecordingCommand command
    ) {
        Recording recording = recordingRepository.findByViolationId(command.violationId())
                .orElseThrow(() -> new RecordingNotFoundForViolationException(command.violationId()));

        if (recording.stopAlreadyHandled()) {
            return recording;
        }

        return recording;
    }
}
