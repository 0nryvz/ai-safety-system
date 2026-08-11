package com.isg.backend.recording.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingTest {

    @Test
    void markRecordingStartedThrowsForInvalidTransition() {
        Recording recording = Recording.rehydrate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RecordingStatus.RECORDING,
                Instant.parse("2026-01-01T10:00:00Z"),
                UUID.randomUUID(),
                null
        );

        assertThatThrownBy(() -> recording.markRecordingStarted(
                Instant.parse("2026-01-01T10:00:05Z"),
                UUID.randomUUID()
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition to RECORDING");
    }

    @Test
    void markProcessingThrowsForInvalidTransition() {
        Recording recording = Recording.rehydrate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RecordingStatus.REQUESTED,
                null,
                UUID.randomUUID(),
                null
        );

        assertThatThrownBy(() -> recording.markProcessing(UUID.randomUUID()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cannot transition to PROCESSING");
    }
}