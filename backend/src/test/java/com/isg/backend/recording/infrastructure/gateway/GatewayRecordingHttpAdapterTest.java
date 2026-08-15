package com.isg.backend.recording.infrastructure.gateway;

import com.isg.backend.recording.application.StartRecordingCommand;
import com.isg.backend.recording.application.StopRecordingCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;

import java.time.Instant;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GatewayRecordingHttpAdapterTest {

    private RestOperations restOperations;
    private GatewayRecordingHttpAdapter adapter;

    @BeforeEach
    void setUp() {
        restOperations = mock(RestOperations.class);
        adapter = new GatewayRecordingHttpAdapter(properties(2), restOperations);
    }

    @Test
    void sendStartMapsPayloadAndCallsExpectedEndpoint() {
        UUID recordingId = UUID.randomUUID();
        StartRecordingCommand command = startCommand(UUID.randomUUID());

        when(restOperations.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.accepted().build());

        adapter.sendStart(recordingId, command);

        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);

        verify(restOperations).postForEntity(urlCaptor.capture(), bodyCaptor.capture(), eq(Void.class));

        assertThat(urlCaptor.getValue())
                .isEqualTo("http://gateway:8081/internal/v1/recordings/commands/start");

        Object payload = bodyCaptor.getValue();
        assertThat(readPayloadField(payload, "commandId")).isEqualTo(command.commandId());
        assertThat(readPayloadField(payload, "recordingId")).isEqualTo(recordingId);
        assertThat(readPayloadField(payload, "violationId")).isEqualTo(command.violationId());
        assertThat(readPayloadField(payload, "cameraId")).isEqualTo(command.cameraId());
        assertThat(readPayloadField(payload, "sessionId")).isEqualTo(command.sessionId());
        assertThat(readPayloadField(payload, "preBufferSeconds")).isEqualTo(command.preBufferSeconds());
        assertThat(readPayloadField(payload, "postBufferSeconds")).isEqualTo(command.postBufferSeconds());
        assertThat(readPayloadField(payload, "maxClipSeconds")).isEqualTo(command.maxClipSeconds());
    }

    @Test
    void sendStopMapsPayloadAndCallsExpectedEndpoint() {
        StopRecordingCommand command = stopCommand(UUID.randomUUID(), UUID.randomUUID());

        when(restOperations.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenReturn(ResponseEntity.accepted().build());

        adapter.sendStop(command);

        org.mockito.ArgumentCaptor<String> urlCaptor = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<Object> bodyCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);

        verify(restOperations).postForEntity(urlCaptor.capture(), bodyCaptor.capture(), eq(Void.class));

        assertThat(urlCaptor.getValue())
                .isEqualTo("http://gateway:8081/internal/v1/recordings/commands/stop");

        Object payload = bodyCaptor.getValue();
        assertThat(readPayloadField(payload, "commandId")).isEqualTo(command.commandId());
        assertThat(readPayloadField(payload, "violationId")).isEqualTo(command.violationId());
    }

    @Test
    void retriesOnTransientFailureAndUsesSameCommandId() {
        UUID recordingId = UUID.randomUUID();
        StartRecordingCommand command = startCommand(UUID.randomUUID());

        when(restOperations.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenThrow(new ResourceAccessException("timeout-2"))
                .thenReturn(ResponseEntity.accepted().build());

        adapter.sendStart(recordingId, command);

        org.mockito.ArgumentCaptor<Object> payloadCaptor = org.mockito.ArgumentCaptor.forClass(Object.class);
        verify(restOperations, times(3)).postForEntity(any(String.class), payloadCaptor.capture(), eq(Void.class));

        List<Object> payloads = payloadCaptor.getAllValues();
        assertThat(payloads).hasSize(3);

        for (Object payload : payloads) {
            assertThat(readPayloadField(payload, "commandId")).isEqualTo(command.commandId());
        }
    }

    @Test
    void doesNotRetryPermanentClientError() {
        UUID recordingId = UUID.randomUUID();
        StartRecordingCommand command = startCommand(UUID.randomUUID());

        when(restOperations.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenThrow(new HttpClientErrorException(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> adapter.sendStart(recordingId, command))
                .isInstanceOf(GatewayRecordingCommandException.class);

        verify(restOperations, times(1)).postForEntity(any(String.class), any(), eq(Void.class));
    }

    @Test
    void doesNotExceedRetryLimit() {
        adapter = new GatewayRecordingHttpAdapter(properties(1), restOperations);
        UUID recordingId = UUID.randomUUID();
        StartRecordingCommand command = startCommand(UUID.randomUUID());

        when(restOperations.postForEntity(any(String.class), any(), eq(Void.class)))
                .thenThrow(new ResourceAccessException("timeout-1"))
                .thenThrow(new ResourceAccessException("timeout-2"));

        assertThatThrownBy(() -> adapter.sendStart(recordingId, command))
                .isInstanceOf(GatewayRecordingCommandException.class);

        verify(restOperations, times(2)).postForEntity(any(String.class), any(), eq(Void.class));
    }

    private GatewayRecordingCommandProperties properties(
            int maxRetries
    ) {
        GatewayRecordingCommandProperties properties = new GatewayRecordingCommandProperties();
        properties.setBaseUrl("http://gateway:8081");
        properties.setMaxRetries(maxRetries);
        return properties;
    }

    private StartRecordingCommand startCommand(
            UUID violationId
    ) {
        return new StartRecordingCommand(
                UUID.randomUUID(),
                violationId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse("2026-01-01T10:00:00Z"),
                5,
                5,
                30
        );
    }

    private StopRecordingCommand stopCommand(
            UUID commandId,
            UUID violationId
    ) {
        return new StopRecordingCommand(
                commandId,
                violationId,
                Instant.parse("2026-01-01T10:00:10Z")
        );
    }

    private Object readPayloadField(
            Object payload,
            String fieldName
    ) {
        try {
            Method accessor = payload.getClass().getDeclaredMethod(fieldName);
            accessor.setAccessible(true);
            return accessor.invoke(payload);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }
}
