package com.isg.backend.recording.controller;

import com.isg.backend.recording.application.RecordingApplicationService;
import com.isg.backend.recording.application.RecordingCallbackCommand;
import com.isg.backend.recording.application.RecordingCallbackConflictException;
import com.isg.backend.recording.application.RecordingNotFoundException;
import com.isg.backend.recording.dto.RecordingCallbackRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/recordings/callback")
public class RecordingCallbackController {

    private final RecordingApplicationService recordingApplicationService;

    public RecordingCallbackController(
            RecordingApplicationService recordingApplicationService
    ) {
        this.recordingApplicationService = recordingApplicationService;
    }

    @PostMapping
    public ResponseEntity<Void> callback(
            @Valid @RequestBody RecordingCallbackRequest request
    ) {
        try {
            recordingApplicationService.handleCallback(new RecordingCallbackCommand(
                    request.recordingId(),
                    request.violationId(),
                    request.status(),
                    request.objectKey(),
                    request.coverImageKey(),
                    request.durationMs(),
                    request.sizeBytes(),
                    request.checksum(),
                    request.retryCount(),
                    request.errorCode()
            ));
            return ResponseEntity.accepted().build();
        } catch (RecordingNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        } catch (RecordingCallbackConflictException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, ex.getMessage(), ex);
        }
    }
}
