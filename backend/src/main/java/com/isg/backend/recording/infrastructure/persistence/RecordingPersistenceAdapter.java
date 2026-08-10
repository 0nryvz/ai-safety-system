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
            existingEntity.setRecordingStartedAt(recording.recordingStartedAt());
            return existingEntity;
        }

        return RecordingJpaEntity.builder()
                .id(recording.id())
                .violationId(recording.violationId())
                .status(recording.status().databaseValue())
                .recordingStartedAt(recording.recordingStartedAt())
                .build();
    }

    private Recording toDomain(
            RecordingJpaEntity entity
    ) {
        return Recording.rehydrate(
                entity.getId(),
                entity.getViolationId(),
                RecordingStatus.fromDatabaseValue(entity.getStatus()),
                entity.getRecordingStartedAt()
        );
    }
}
