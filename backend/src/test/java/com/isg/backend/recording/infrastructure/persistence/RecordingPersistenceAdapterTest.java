package com.isg.backend.recording.infrastructure.persistence;

import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

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
}
