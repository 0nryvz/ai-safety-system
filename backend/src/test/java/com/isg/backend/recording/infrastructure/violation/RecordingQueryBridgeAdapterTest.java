package com.isg.backend.recording.infrastructure.violation;

import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import com.isg.backend.violation.application.port.RecordingQueryResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingQueryBridgeAdapterTest {

    private RecordingRepository recordingRepository;
    private RecordingQueryBridgeAdapter adapter;

    @BeforeEach
    void setUp() {
        recordingRepository = mock(RecordingRepository.class);

        adapter = new RecordingQueryBridgeAdapter(
                recordingRepository
        );
    }

    @Test
    void returnsEmptyWhenRecordingDoesNotExist() {
        UUID violationId = UUID.randomUUID();

        when(
                recordingRepository.findByViolationId(
                        violationId
                )
        ).thenReturn(
                Optional.empty()
        );

        Optional<RecordingQueryResult> result =
                adapter.findByViolationId(
                        violationId
                );

        assertThat(result).isEmpty();

        verify(
                recordingRepository
        ).findByViolationId(
                violationId
        );
    }

    @Test
    void returnsNotReadyWhenRecordingIsStillProcessing() {
        UUID violationId = UUID.randomUUID();

        Recording recording = Recording.rehydrate(
                UUID.randomUUID(),
                violationId,
                RecordingStatus.PROCESSING,
                null,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        when(
                recordingRepository.findByViolationId(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        recording
                )
        );

        Optional<RecordingQueryResult> result =
                adapter.findByViolationId(
                        violationId
                );

        assertThat(result).isPresent();
        assertThat(
                result.orElseThrow().recordingStatus()
        ).isEqualTo(
                "PROCESSING"
        );
        assertThat(
                result.orElseThrow().clipReady()
        ).isFalse();
        assertThat(
                result.orElseThrow().playbackUrl()
        ).isNull();
    }

    @Test
    void returnsReadyWhenRecordingHasReadyClip() {
        UUID violationId = UUID.randomUUID();

        Recording recording = Recording.rehydrate(
                UUID.randomUUID(),
                violationId,
                RecordingStatus.READY,
                "violations/2026/08/violation/recording.mp4",
                30_000,
                700_000L,
                0,
                "sha256:test",
                null,
                Instant.parse(
                        "2026-08-18T02:00:00Z"
                ),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse(
                        "2026-08-18T02:00:30Z"
                )
        );

        when(
                recordingRepository.findByViolationId(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        recording
                )
        );

        RecordingQueryResult result =
                adapter.findByViolationId(
                                violationId
                        )
                        .orElseThrow();

        assertThat(
                result.recordingStatus()
        ).isEqualTo(
                "READY"
        );

        assertThat(
                result.clipReady()
        ).isTrue();

        assertThat(
                result.playbackUrl()
        ).isNull();
    }

    @Test
    void readyStatusWithoutObjectKeyIsNotClipReady() {
        UUID violationId = UUID.randomUUID();

        Recording recording = Recording.rehydrate(
                UUID.randomUUID(),
                violationId,
                RecordingStatus.READY,
                null,
                30_000,
                700_000L,
                0,
                "sha256:test",
                null,
                Instant.parse(
                        "2026-08-18T02:00:00Z"
                ),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse(
                        "2026-08-18T02:00:30Z"
                )
        );

        when(
                recordingRepository.findByViolationId(
                        violationId
                )
        ).thenReturn(
                Optional.of(
                        recording
                )
        );

        RecordingQueryResult result =
                adapter.findByViolationId(
                                violationId
                        )
                        .orElseThrow();

        assertThat(
                result.recordingStatus()
        ).isEqualTo(
                "READY"
        );

        assertThat(
                result.clipReady()
        ).isFalse();

        assertThat(
                result.playbackUrl()
        ).isNull();
    }

    @Test
    void returnsRecordingResultsInBulk() {
        UUID violationId1 = UUID.randomUUID();
        UUID violationId2 = UUID.randomUUID();

        Recording requestedRecording = Recording.rehydrate(
                UUID.randomUUID(),
                violationId1,
                RecordingStatus.REQUESTED,
                null,
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        Recording readyRecording = Recording.rehydrate(
                UUID.randomUUID(),
                violationId2,
                RecordingStatus.READY,
                "violations/test/recording.mp4",
                30_000,
                700_000L,
                0,
                "sha256:test",
                null,
                Instant.parse(
                        "2026-08-19T04:00:00Z"
                ),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.parse(
                        "2026-08-19T04:00:30Z"
                )
        );

        when(
                recordingRepository.findByViolationIds(
                        List.of(
                                violationId1,
                                violationId2
                        )
                )
        ).thenReturn(
                Map.of(
                        violationId1,
                        requestedRecording,
                        violationId2,
                        readyRecording
                )
        );

        Map<UUID, RecordingQueryResult> results =
                adapter.findByViolationIds(
                        List.of(
                                violationId1,
                                violationId2
                        )
                );

        assertThat(results).hasSize(2);

        assertThat(
                results.get(
                        violationId1
                ).recordingStatus()
        ).isEqualTo(
                "REQUESTED"
        );

        assertThat(
                results.get(
                        violationId1
                ).clipReady()
        ).isFalse();

        assertThat(
                results.get(
                        violationId2
                ).recordingStatus()
        ).isEqualTo(
                "READY"
        );

        assertThat(
                results.get(
                        violationId2
                ).clipReady()
        ).isTrue();

        verify(
                recordingRepository
        ).findByViolationIds(
                List.of(
                        violationId1,
                        violationId2
                )
        );
    }

    @Test
    void delegatesRecordingStatusFilterUsingDomainStatus() {
        UUID violationId1 = UUID.randomUUID();
        UUID violationId2 = UUID.randomUUID();

        when(
                recordingRepository.findViolationIdsByStatus(
                        RecordingStatus.REQUESTED
                )
        ).thenReturn(
                Set.of(
                        violationId1,
                        violationId2
                )
        );

        Set<UUID> violationIds =
                adapter.findViolationIdsByRecordingStatus(
                        "REQUESTED"
                );

        assertThat(violationIds)
                .containsExactlyInAnyOrder(
                        violationId1,
                        violationId2
                );

        verify(
                recordingRepository
        ).findViolationIdsByStatus(
                RecordingStatus.REQUESTED
        );
    }

}