package com.isg.backend.recording.application;

import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingApplicationServiceTest {

    private InMemoryRecordingRepository repository;
    private RecordingApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRecordingRepository();
        service = new RecordingApplicationService(repository);
    }

    @Test
    void firstStartCreatesRecording() {
        UUID violationId = UUID.randomUUID();

        Recording created = service.start(startCommand(violationId));

        assertThat(created.id()).isNotNull();
        assertThat(created.violationId()).isEqualTo(violationId);
        assertThat(created.status()).isEqualTo(RecordingStatus.REQUESTED);
        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    void duplicateStartDoesNotCreateSecondRecordingAndReturnsExisting() {
        UUID violationId = UUID.randomUUID();

        Recording first = service.start(startCommand(violationId));
        Recording second = service.start(startCommand(violationId));

        assertThat(repository.size()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.violationId()).isEqualTo(first.violationId());
    }

    @Test
    void differentViolationsCreateDifferentRecordings() {
        Recording first = service.start(startCommand(UUID.randomUUID()));
        Recording second = service.start(startCommand(UUID.randomUUID()));

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(repository.size()).isEqualTo(2);
    }

    @Test
    void stopKeepsExistingRecordingRequestedUntilGatewayAckFlowExists() {
        UUID violationId = UUID.randomUUID();
        service.start(startCommand(violationId));

        Recording stopped = service.stop(stopCommand(violationId));

        assertThat(stopped.status()).isEqualTo(RecordingStatus.REQUESTED);
        assertThat(repository.findByViolationId(violationId)).hasValue(stopped);
    }

    @Test
    void stopWithoutStartThrowsControlledError() {
        UUID missingViolationId = UUID.randomUUID();

        assertThatThrownBy(() -> service.stop(stopCommand(missingViolationId)))
                .isInstanceOf(RecordingNotFoundForViolationException.class)
                .hasMessageContaining(missingViolationId.toString());
    }

    @Test
    void duplicateStopIsSafeAndIdempotent() {
        UUID violationId = UUID.randomUUID();
        service.start(startCommand(violationId));

        Recording firstStop = service.stop(stopCommand(violationId));
        Recording secondStop = service.stop(stopCommand(violationId));

        assertThat(firstStop.status()).isEqualTo(RecordingStatus.REQUESTED);
        assertThat(secondStop.status()).isEqualTo(RecordingStatus.REQUESTED);
        assertThat(secondStop.id()).isEqualTo(firstStop.id());
        assertThat(repository.size()).isEqualTo(1);
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
            UUID violationId
    ) {
        return new StopRecordingCommand(
                UUID.randomUUID(),
                violationId,
                Instant.parse("2026-01-01T10:00:10Z")
        );
    }

    private static class InMemoryRecordingRepository implements RecordingRepository {

        private final Map<UUID, Recording> byViolationId = new HashMap<>();

        @Override
        public Optional<Recording> findByViolationId(
                UUID violationId
        ) {
            return Optional.ofNullable(byViolationId.get(violationId));
        }

        @Override
        public Recording save(
                Recording recording
        ) {
            Recording persisted = recording.id() == null
                    ? Recording.rehydrate(
                    UUID.randomUUID(),
                    recording.violationId(),
                    recording.status(),
                    recording.recordingStartedAt()
            )
                    : recording;

            byViolationId.put(persisted.violationId(), persisted);
            return persisted;
        }

        int size() {
            return byViolationId.size();
        }
    }
}
