package com.isg.backend.recording.infrastructure.violation;

import com.isg.backend.recording.application.RecordingApplicationService;
import com.isg.backend.recording.application.StartRecordingCommand;
import com.isg.backend.recording.application.StopRecordingCommand;
import com.isg.backend.recording.config.RecordingCommandProperties;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import com.isg.backend.violation.application.event.ViolationEndedEvent;
import com.isg.backend.violation.application.event.ViolationStartedEvent;
import com.isg.backend.violation.domain.ViolationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingCommandBridgeAdapterTest {

    private RecordingApplicationService recordingApplicationService;
    private RecordingCommandBridgeAdapter bridgeAdapter;

    @BeforeEach
    void setUp() {
        recordingApplicationService = mock(RecordingApplicationService.class);

        RecordingCommandProperties commandProperties = new RecordingCommandProperties();
        commandProperties.setPreBufferSeconds(6);
        commandProperties.setPostBufferSeconds(8);
        commandProperties.setMaxClipSeconds(40);

        bridgeAdapter = new RecordingCommandBridgeAdapter(
                recordingApplicationService,
                commandProperties
        );

        when(recordingApplicationService.start(any(StartRecordingCommand.class)))
                .thenReturn(sampleRecording(UUID.randomUUID()));

        when(recordingApplicationService.stop(any(StopRecordingCommand.class)))
                .thenReturn(sampleRecording(UUID.randomUUID()));
    }

    @Test
    void startEventMapsToStartRecordingCommand() {
        UUID commandId = UUID.randomUUID();
        UUID violationId = UUID.randomUUID();
        UUID cameraId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        Instant startedAt = Instant.parse("2026-01-01T10:00:00Z");

        bridgeAdapter.startRecording(new ViolationStartedEvent(
                commandId,
                violationId,
                cameraId,
                sessionId,
                ViolationType.MISSING_GLOVES,
                startedAt
        ));

        ArgumentCaptor<StartRecordingCommand> captor = ArgumentCaptor.forClass(StartRecordingCommand.class);
        verify(recordingApplicationService).start(captor.capture());

        StartRecordingCommand command = captor.getValue();
        assertThat(command.commandId()).isEqualTo(commandId);
        assertThat(command.violationId()).isEqualTo(violationId);
        assertThat(command.cameraId()).isEqualTo(cameraId);
        assertThat(command.sessionId()).isEqualTo(sessionId);
        assertThat(command.startedAt()).isEqualTo(startedAt);
        assertThat(command.preBufferSeconds()).isEqualTo(6);
        assertThat(command.postBufferSeconds()).isEqualTo(8);
        assertThat(command.maxClipSeconds()).isEqualTo(40);
    }

    @Test
    void stopEventMapsToStopRecordingCommand() {
        UUID commandId = UUID.randomUUID();
        UUID violationId = UUID.randomUUID();
        Instant endedAt = Instant.parse("2026-01-01T10:00:10Z");

        bridgeAdapter.stopRecording(new ViolationEndedEvent(
                commandId,
                violationId,
                endedAt
        ));

        ArgumentCaptor<StopRecordingCommand> captor = ArgumentCaptor.forClass(StopRecordingCommand.class);
        verify(recordingApplicationService).stop(captor.capture());

        StopRecordingCommand command = captor.getValue();
        assertThat(command.commandId()).isEqualTo(commandId);
        assertThat(command.violationId()).isEqualTo(violationId);
        assertThat(command.endedAt()).isEqualTo(endedAt);
    }

    private Recording sampleRecording(
            UUID violationId
    ) {
        return Recording.rehydrate(
                UUID.randomUUID(),
                violationId,
                RecordingStatus.REQUESTED,
                null,
                UUID.randomUUID(),
                null
        );
    }
}