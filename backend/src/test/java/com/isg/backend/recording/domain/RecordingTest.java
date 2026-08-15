package com.isg.backend.recording.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingTest {

    @Test
    void markReadyStoresMetadataAndTransitionsToReady() {
        Recording recording = Recording.rehydrate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RecordingStatus.PROCESSING,
                Instant.parse("2026-01-01T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        Instant readyAt = Instant.parse("2026-01-01T10:05:00Z");
        recording.markReady(
                "recordings/object.mp4",
                2_000,
                4_096,
                readyAt,
                "sha256:abc"
        );

        assertThat(recording.status()).isEqualTo(RecordingStatus.READY);
        assertThat(recording.objectKey()).isEqualTo("recordings/object.mp4");
        assertThat(recording.durationMs()).isEqualTo(2_000);
        assertThat(recording.sizeBytes()).isEqualTo(4_096L);
        assertThat(recording.readyAt()).isEqualTo(readyAt);
        assertThat(recording.checksum()).isEqualTo("sha256:abc");
    }

    @Test
    void markReadyRejectsInvalidMetadata() {
        Recording recording = Recording.rehydrate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RecordingStatus.PROCESSING,
                Instant.parse("2026-01-01T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> recording.markReady(
                "   ",
                2_000,
                4_096,
                Instant.now(),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("objectKey");

        assertThatThrownBy(() -> recording.markReady(
                "recordings/object.mp4",
                0,
                4_096,
                Instant.now(),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationMs");

        assertThatThrownBy(() -> recording.markReady(
                "recordings/object.mp4",
                2_000,
                0,
                Instant.now(),
                null
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sizeBytes");

        assertThatThrownBy(() -> recording.markReady(
                "recordings/object.mp4",
                2_000,
                4_096,
                null,
                null
        )).isInstanceOf(NullPointerException.class)
                .hasMessageContaining("readyAt");
    }

    @Test
    void markErrorSetsErrorCodeAndTransitionsToError() {
        Recording recording = Recording.rehydrate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RecordingStatus.PROCESSING,
                Instant.parse("2026-01-01T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        recording.markError("UPLOAD_FAILED");

        assertThat(recording.status()).isEqualTo(RecordingStatus.ERROR);
        assertThat(recording.errorCode()).isEqualTo("UPLOAD_FAILED");
    }

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

    @Test
    void markErrorRequiresErrorCode() {
        Recording recording = Recording.rehydrate(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RecordingStatus.PROCESSING,
                Instant.parse("2026-01-01T10:00:00Z"),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        assertThatThrownBy(() -> recording.markError(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("errorCode");
    }
}