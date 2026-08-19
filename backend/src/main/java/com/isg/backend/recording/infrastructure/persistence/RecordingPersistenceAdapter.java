package com.isg.backend.recording.infrastructure.persistence;

import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class RecordingPersistenceAdapter implements RecordingRepository {

    private final SpringDataRecordingRepository repository;

    public RecordingPersistenceAdapter(
            SpringDataRecordingRepository repository
    ) {
        this.repository = repository;
    }

    @Override
    public Optional<Recording> findById(
            UUID recordingId
    ) {
        return repository.findById(recordingId)
                .map(this::toDomain);
    }

    @Override
    public Optional<Recording> findByViolationId(
            UUID violationId
    ) {
        return repository.findByViolationId(violationId)
                .map(this::toDomain);
    }

    @Override
    public Recording save(
            Recording recording
    ) {
        RecordingJpaEntity entity = repository.save(toEntityForSave(recording));
        return toDomain(entity);
    }

    private RecordingJpaEntity toEntityForSave(
            Recording recording
    ) {
        if (recording.id() != null) {
            RecordingJpaEntity existingEntity = repository.findById(recording.id())
                    .orElseThrow(() -> new IllegalStateException(
                            "Recording entity not found for id=" + recording.id()
                    ));

            existingEntity.setStatus(recording.status().databaseValue());
            if (recording.objectKey() != null) {
                existingEntity.setObjectKey(recording.objectKey());
            }
            if (recording.durationMs() != null) {
                existingEntity.setDurationMs(recording.durationMs());
            }
            if (recording.sizeBytes() != null) {
                existingEntity.setSizeBytes(recording.sizeBytes());
            }
            if (recording.retryCount() != null) {
                existingEntity.setRetryCount(recording.retryCount());
            }
            if (recording.checksum() != null) {
                existingEntity.setChecksum(recording.checksum());
            }
            if (recording.errorCode() != null) {
                existingEntity.setErrorCode(recording.errorCode());
            }
            existingEntity.setRecordingStartedAt(recording.recordingStartedAt());
            existingEntity.setStartCommandId(recording.startCommandId());
            existingEntity.setStopCommandId(recording.stopCommandId());
            if (recording.readyAt() != null) {
                existingEntity.setReadyAt(recording.readyAt());
            }
            return existingEntity;
        }

        return RecordingJpaEntity.builder()
                .id(recording.id())
                .violationId(recording.violationId())
                .status(recording.status().databaseValue())
                .objectKey(recording.objectKey())
                .durationMs(recording.durationMs())
                .sizeBytes(recording.sizeBytes())
                .retryCount(recording.retryCount() == null ? 0 : recording.retryCount())
                .checksum(recording.checksum())
                .errorCode(recording.errorCode())
                .recordingStartedAt(recording.recordingStartedAt())
                .startCommandId(recording.startCommandId())
                .stopCommandId(recording.stopCommandId())
                .readyAt(recording.readyAt())
                .build();
    }

    private Recording toDomain(
            RecordingJpaEntity entity
    ) {
        return Recording.rehydrate(
                entity.getId(),
                entity.getViolationId(),
                RecordingStatus.fromDatabaseValue(entity.getStatus()),
                entity.getObjectKey(),
                entity.getDurationMs(),
                entity.getSizeBytes(),
                entity.getRetryCount(),
                entity.getChecksum(),
                entity.getErrorCode(),
                entity.getRecordingStartedAt(),
                entity.getStartCommandId(),
                entity.getStopCommandId(),
                entity.getReadyAt()
        );
    }
}
