package com.isg.backend.recording.infrastructure.persistence;

import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecordingPersistenceAdapterTest {

    private SpringDataRecordingRepository springDataRepository;
    private RecordingPersistenceAdapter adapter;

    @BeforeEach
    void setUp() {
        springDataRepository = mock(SpringDataRecordingRepository.class);
        adapter = new RecordingPersistenceAdapter(springDataRepository);
    }

    @Test
    void savesRequestedStatusAsPendingInDatabaseRepresentation() {
        UUID violationId = UUID.randomUUID();
        UUID startCommandId = UUID.randomUUID();
        RecordingJpaEntity persistedEntity = RecordingJpaEntity.builder()
                .id(UUID.randomUUID())
                .violationId(violationId)
                .status("PENDING")
                .startCommandId(startCommandId)
                .build();

        when(springDataRepository.save(any(RecordingJpaEntity.class)))
                .thenReturn(persistedEntity);

        Recording saved = adapter.save(Recording.createRequested(violationId, startCommandId));

        verify(springDataRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
                entity.getStatus().equals("PENDING")
                        && entity.getViolationId().equals(violationId)
                        && entity.getStartCommandId().equals(startCommandId)
        ));
        assertThat(saved.status()).isEqualTo(RecordingStatus.REQUESTED);
        assertThat(saved.startCommandId()).isEqualTo(startCommandId);
    }

    @Test
    void preservesExistingMetadataFieldsWhenUpdatingOwnedDomainFields() {
        UUID id = UUID.randomUUID();
        UUID violationId = UUID.randomUUID();

        RecordingJpaEntity existingEntity = RecordingJpaEntity.builder()
                .id(id)
                .violationId(violationId)
                .status("READY")
                .objectKey("clips/v1/video.mp4")
                .durationMs(2_500)
                .sizeBytes(10_000L)
                .retryCount(2)
                .checksum("sha256:abc")
                .errorCode("ERR_TIMEOUT")
                .recordingStartedAt(Instant.parse("2026-01-01T09:59:00Z"))
                .startCommandId(UUID.randomUUID())
                .stopCommandId(UUID.randomUUID())
                .readyAt(Instant.parse("2026-01-01T10:00:00Z"))
                .build();

        when(springDataRepository.findById(id))
                .thenReturn(Optional.of(existingEntity));

        when(springDataRepository.save(any(RecordingJpaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Recording domainRecording = Recording.rehydrate(
                id,
                violationId,
                RecordingStatus.RECORDING,
                Instant.parse("2026-01-01T10:01:00Z"),
                existingEntity.getStartCommandId(),
                existingEntity.getStopCommandId()
        );

        Recording saved = adapter.save(domainRecording);

        verify(springDataRepository).save(org.mockito.ArgumentMatchers.argThat(entity ->
                entity.getId().equals(id)
                        && entity.getViolationId().equals(violationId)
                        && entity.getStatus().equals("RECORDING")
                        && entity.getRecordingStartedAt().equals(Instant.parse("2026-01-01T10:01:00Z"))
                        && entity.getObjectKey().equals("clips/v1/video.mp4")
                        && entity.getDurationMs().equals(2_500)
                        && entity.getSizeBytes().equals(10_000L)
                        && entity.getRetryCount() == 2
                        && entity.getChecksum().equals("sha256:abc")
                        && entity.getErrorCode().equals("ERR_TIMEOUT")
                        && entity.getStartCommandId().equals(existingEntity.getStartCommandId())
                        && entity.getStopCommandId().equals(existingEntity.getStopCommandId())
                        && entity.getReadyAt().equals(Instant.parse("2026-01-01T10:00:00Z"))
        ));

        assertThat(saved.id()).isEqualTo(id);
        assertThat(saved.violationId()).isEqualTo(violationId);
        assertThat(saved.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(saved.recordingStartedAt()).isEqualTo(Instant.parse("2026-01-01T10:01:00Z"));
        assertThat(saved.objectKey()).isEqualTo("clips/v1/video.mp4");
        assertThat(saved.durationMs()).isEqualTo(2_500);
        assertThat(saved.sizeBytes()).isEqualTo(10_000L);
        assertThat(saved.retryCount()).isEqualTo(2);
        assertThat(saved.checksum()).isEqualTo("sha256:abc");
        assertThat(saved.errorCode()).isEqualTo("ERR_TIMEOUT");
        assertThat(saved.readyAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
    }

    @Test
    void mapsPendingDatabaseValueBackToRequestedDomainStatus() {
        UUID violationId = UUID.randomUUID();
        RecordingJpaEntity entity = RecordingJpaEntity.builder()
                .id(UUID.randomUUID())
                .violationId(violationId)
                .status("PENDING")
                .recordingStartedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .startCommandId(UUID.randomUUID())
                .build();

        when(springDataRepository.findByViolationId(violationId))
                .thenReturn(Optional.of(entity));

        Recording loaded = adapter.findByViolationId(violationId).orElseThrow();

        assertThat(loaded.status()).isEqualTo(RecordingStatus.REQUESTED);
        assertThat(loaded.recordingStartedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(loaded.startCommandId()).isEqualTo(entity.getStartCommandId());
    }

    @Test
    void findByIdMapsAllMetadataFieldsToDomain() {
        UUID recordingId = UUID.randomUUID();
        UUID violationId = UUID.randomUUID();
        UUID startCommandId = UUID.randomUUID();
        UUID stopCommandId = UUID.randomUUID();

        RecordingJpaEntity entity = RecordingJpaEntity.builder()
                .id(recordingId)
                .violationId(violationId)
                .status("READY")
                .objectKey("clips/v2/object.mp4")
                .durationMs(3_400)
                .sizeBytes(15_000L)
                .retryCount(4)
                .checksum("sha256:def")
                .errorCode("ERR_FINAL")
                .recordingStartedAt(Instant.parse("2026-01-01T10:00:00Z"))
                .startCommandId(startCommandId)
                .stopCommandId(stopCommandId)
                .readyAt(Instant.parse("2026-01-01T10:02:00Z"))
                .build();

        when(springDataRepository.findById(recordingId))
                .thenReturn(Optional.of(entity));

        Recording loaded = adapter.findById(recordingId).orElseThrow();

        assertThat(loaded.id()).isEqualTo(recordingId);
        assertThat(loaded.violationId()).isEqualTo(violationId);
        assertThat(loaded.status()).isEqualTo(RecordingStatus.READY);
        assertThat(loaded.objectKey()).isEqualTo("clips/v2/object.mp4");
        assertThat(loaded.durationMs()).isEqualTo(3_400);
        assertThat(loaded.sizeBytes()).isEqualTo(15_000L);
        assertThat(loaded.retryCount()).isEqualTo(4);
        assertThat(loaded.checksum()).isEqualTo("sha256:def");
        assertThat(loaded.errorCode()).isEqualTo("ERR_FINAL");
        assertThat(loaded.recordingStartedAt()).isEqualTo(Instant.parse("2026-01-01T10:00:00Z"));
        assertThat(loaded.startCommandId()).isEqualTo(startCommandId);
        assertThat(loaded.stopCommandId()).isEqualTo(stopCommandId);
        assertThat(loaded.readyAt()).isEqualTo(Instant.parse("2026-01-01T10:02:00Z"));
    }

    @Test
    void findsRecordingsInBulkByViolationIds() {
        UUID violationId1 = UUID.randomUUID();
        UUID violationId2 = UUID.randomUUID();

        RecordingJpaEntity pendingEntity = RecordingJpaEntity.builder()
                .id(UUID.randomUUID())
                .violationId(violationId1)
                .status("PENDING")
                .startCommandId(UUID.randomUUID())
                .build();

        RecordingJpaEntity readyEntity = RecordingJpaEntity.builder()
                .id(UUID.randomUUID())
                .violationId(violationId2)
                .status("READY")
                .startCommandId(UUID.randomUUID())
                .build();

        when(
                springDataRepository.findByViolationIdIn(
                        List.of(
                                violationId1,
                                violationId2
                        )
                )
        ).thenReturn(
                List.of(
                        pendingEntity,
                        readyEntity
                )
        );

        Map<UUID, Recording> recordings =
                adapter.findByViolationIds(
                        List.of(
                                violationId1,
                                violationId2
                        )
                );

        assertThat(recordings).hasSize(2);

        assertThat(
                recordings.get(violationId1).status()
        ).isEqualTo(
                RecordingStatus.REQUESTED
        );

        assertThat(
                recordings.get(violationId2).status()
        ).isEqualTo(
                RecordingStatus.READY
        );

        verify(
                springDataRepository
        ).findByViolationIdIn(
                List.of(
                        violationId1,
                        violationId2
                )
        );
    }

    @Test
    void mapsRequestedStatusToPendingWhenFilteringViolationIds() {
        UUID violationId1 = UUID.randomUUID();
        UUID violationId2 = UUID.randomUUID();

        when(
                springDataRepository.findViolationIdsByStatus(
                        "PENDING"
                )
        ).thenReturn(
                Set.of(
                        violationId1,
                        violationId2
                )
        );

        Set<UUID> violationIds =
                adapter.findViolationIdsByStatus(
                        RecordingStatus.REQUESTED
                );

        assertThat(violationIds)
                .containsExactlyInAnyOrder(
                        violationId1,
                        violationId2
                );

        verify(
                springDataRepository
        ).findViolationIdsByStatus(
                "PENDING"
        );
    }

}
