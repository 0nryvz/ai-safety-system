package com.isg.backend.recording.infrastructure.violation;

import com.isg.backend.recording.application.callback.RecordingStatusCallback;
import com.isg.backend.recording.domain.RecordingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RecordingStatusCallbackBridgeAdapterTest {

    private com.isg.backend.violation.application.port.RecordingStatusCallbackPort callbackPort;
    private RecordingStatusCallbackBridgeAdapter bridgeAdapter;

    @BeforeEach
    void setUp() {
        callbackPort = mock(com.isg.backend.violation.application.port.RecordingStatusCallbackPort.class);
        bridgeAdapter = new RecordingStatusCallbackBridgeAdapter(
                callbackPort,
                Clock.fixed(
                        Instant.parse("2026-01-01T11:00:00Z"),
                        ZoneOffset.UTC
                )
        );
    }

    @Test
    void readyStatusMapsToViolationRecordingReady() {
        UUID violationId = UUID.randomUUID();

        bridgeAdapter.publish(new RecordingStatusCallback(
                UUID.randomUUID(),
                violationId,
                RecordingStatus.READY,
                "obj-key",
                "violations/2026/08/violation-1/cover.jpg",
                1_000L,
                2_000L,
                "checksum",
                null
        ));

        verify(callbackPort).recordingReady(
                eq(violationId),
                eq(Instant.parse("2026-01-01T11:00:00Z")),
                eq("violations/2026/08/violation-1/cover.jpg")
        );
    }

    @Test
    void errorStatusMapsToViolationRecordingError() {
        UUID violationId = UUID.randomUUID();

        bridgeAdapter.publish(new RecordingStatusCallback(
                UUID.randomUUID(),
                violationId,
                RecordingStatus.ERROR,
                null,
                null,
                null,
                null,
                "GATEWAY_TIMEOUT"
        ));

        verify(callbackPort).recordingError(
                eq(violationId),
                eq(Instant.parse("2026-01-01T11:00:00Z")),
                eq("GATEWAY_TIMEOUT")
        );
    }

    @Test
    void nonTerminalStatusesDoNotCallViolationCallbackPort() {
        bridgeAdapter.publish(new RecordingStatusCallback(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RecordingStatus.PROCESSING,
                null,
                null,
                null,
                null,
                null
        ));

        verifyNoInteractions(callbackPort);
    }
}