package com.isg.backend.recording.application;

import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingApplicationServiceTest {

    private InMemoryRecordingRepository repository;
    private CapturingGatewayRecordingCommandPort gatewayCommandPort;
    private RecordingApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRecordingRepository();
        gatewayCommandPort = new CapturingGatewayRecordingCommandPort();
        service = new RecordingApplicationService(
                repository,
                new RecordingCreationService(repository),
                gatewayCommandPort
        );
    }

    @Test
    void firstStartCreatesRecording() {
        UUID violationId = UUID.randomUUID();
        StartRecordingCommand command = startCommand(violationId);

        Recording created = service.start(command);

        assertThat(created.id()).isNotNull();
        assertThat(created.violationId()).isEqualTo(violationId);
        assertThat(created.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(created.startCommandId()).isEqualTo(command.commandId());
        assertThat(repository.size()).isEqualTo(1);
        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(1);
        assertThat(gatewayCommandPort.lastStartRecordingId()).isEqualTo(created.id());
    }

    @Test
    void duplicateStartDoesNotCreateSecondRecordingAndReturnsExisting() {
        UUID violationId = UUID.randomUUID();

        Recording first = service.start(startCommand(violationId));
        Recording second = service.start(startCommand(violationId));

        assertThat(repository.size()).isEqualTo(1);
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(second.violationId()).isEqualTo(first.violationId());
        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(1);
    }

    @Test
    void differentViolationsCreateDifferentRecordings() {
        Recording first = service.start(startCommand(UUID.randomUUID()));
        Recording second = service.start(startCommand(UUID.randomUUID()));

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(repository.size()).isEqualTo(2);
        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(2);
    }

    @Test
    void stopUpdatesExistingRecordingToProcessingAfterGatewayAck() {
        UUID violationId = UUID.randomUUID();
        service.start(startCommand(violationId));

        Recording stopped = service.stop(stopCommand(violationId));

        assertThat(stopped.status()).isEqualTo(RecordingStatus.PROCESSING);
        assertThat(repository.findByViolationId(violationId)).hasValue(stopped);
        assertThat(gatewayCommandPort.stopCommandCount()).isEqualTo(1);
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

        StopRecordingCommand firstCommand = stopCommand(violationId);
        Recording firstStop = service.stop(firstCommand);
        Recording secondStop = service.stop(stopCommand(violationId));

        assertThat(firstStop.status()).isEqualTo(RecordingStatus.PROCESSING);
        assertThat(secondStop.status()).isEqualTo(RecordingStatus.PROCESSING);
        assertThat(secondStop.id()).isEqualTo(firstStop.id());
        assertThat(secondStop.stopCommandId()).isEqualTo(firstCommand.commandId());
        assertThat(repository.size()).isEqualTo(1);
        assertThat(gatewayCommandPort.stopCommandCount()).isEqualTo(1);
    }

    @Test
    void startFailureDoesNotAdvanceRecordingToRecordingStatus() {
        UUID violationId = UUID.randomUUID();
        gatewayCommandPort.failStart = true;
        StartRecordingCommand command = startCommand(violationId);

        assertThatThrownBy(() -> service.start(command))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("start failed");

        assertThat(repository.findByViolationId(violationId))
                .hasValueSatisfying(recording -> {
                    assertThat(recording.status()).isEqualTo(RecordingStatus.REQUESTED);
                    assertThat(recording.startCommandId()).isEqualTo(command.commandId());
                });
    }

    @Test
    void failedStartDeliveryRetryUsesPersistedStartCommandId() {
        UUID violationId = UUID.randomUUID();
        StartRecordingCommand firstCommand = startCommand(violationId);

        gatewayCommandPort.failStart = true;
        assertThatThrownBy(() -> service.start(firstCommand))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("start failed");

        gatewayCommandPort.failStart = false;
        StartRecordingCommand retryEventCommand = startCommand(violationId);
        Recording result = service.start(retryEventCommand);

        assertThat(result.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(result.startCommandId()).isEqualTo(firstCommand.commandId());
        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(1);
        assertThat(gatewayCommandPort.lastStartCommand().commandId()).isEqualTo(firstCommand.commandId());
    }

    @Test
    void stopFailureDoesNotAdvanceRecordingToProcessingStatus() {
        UUID violationId = UUID.randomUUID();
        service.start(startCommand(violationId));
        gatewayCommandPort.failStop = true;

        assertThatThrownBy(() -> service.stop(stopCommand(violationId)))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("stop failed");

        assertThat(repository.findByViolationId(violationId))
                .hasValueSatisfying(recording -> {
                    assertThat(recording.status()).isEqualTo(RecordingStatus.RECORDING);
                    assertThat(recording.stopCommandId()).isNotNull();
                });
    }

    @Test
    void failedStopDeliveryRetryUsesPersistedStopCommandId() {
        UUID violationId = UUID.randomUUID();
        service.start(startCommand(violationId));

        StopRecordingCommand firstCommand = stopCommand(violationId);
        gatewayCommandPort.failStop = true;
        assertThatThrownBy(() -> service.stop(firstCommand))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("stop failed");

        gatewayCommandPort.failStop = false;
        StopRecordingCommand retryEventCommand = stopCommand(violationId);
        Recording stopped = service.stop(retryEventCommand);

        assertThat(stopped.status()).isEqualTo(RecordingStatus.PROCESSING);
        assertThat(stopped.stopCommandId()).isEqualTo(firstCommand.commandId());
        assertThat(gatewayCommandPort.stopCommandCount()).isEqualTo(1);
        assertThat(gatewayCommandPort.lastStopCommand().commandId()).isEqualTo(firstCommand.commandId());
    }

    @Test
    void concurrentDuplicateStartDataIntegrityViolationIsHandledIdempotently() {
        UUID violationId = UUID.randomUUID();
        UUID persistedCommandId = UUID.randomUUID();
        ConcurrentDuplicateStartRepository concurrentRepository =
                new ConcurrentDuplicateStartRepository(
                        violationId,
                        persistedCommandId
                );
        RecordingApplicationService concurrentService = new RecordingApplicationService(
                concurrentRepository,
                new RecordingCreationService(concurrentRepository),
                gatewayCommandPort
        );

        Recording created = concurrentService.start(startCommand(violationId));

        assertThat(created.violationId()).isEqualTo(violationId);
        assertThat(created.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(created.startCommandId()).isEqualTo(persistedCommandId);
        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(1);
        assertThat(gatewayCommandPort.lastStartCommand().commandId()).isEqualTo(persistedCommandId);
        assertThat(concurrentRepository.size()).isEqualTo(1);
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
                    recording.recordingStartedAt(),
                    recording.startCommandId(),
                    recording.stopCommandId()
            )
                    : recording;

            byViolationId.put(persisted.violationId(), persisted);
            return persisted;
        }

        int size() {
            return byViolationId.size();
        }
    }

    private static class CapturingGatewayRecordingCommandPort implements GatewayRecordingCommandPort {

        private final List<UUID> sentStartRecordingIds = new ArrayList<>();
        private final List<StartRecordingCommand> sentStartCommands = new ArrayList<>();
        private final List<StopRecordingCommand> sentStopCommands = new ArrayList<>();
        private boolean failStart;
        private boolean failStop;

        @Override
        public void sendStart(
                UUID recordingId,
                StartRecordingCommand command
        ) {
            if (failStart) {
                throw new RuntimeException("start failed");
            }

            sentStartRecordingIds.add(recordingId);
            sentStartCommands.add(command);
        }

        @Override
        public void sendStop(
                StopRecordingCommand command
        ) {
            if (failStop) {
                throw new RuntimeException("stop failed");
            }

            sentStopCommands.add(command);
        }

        int startCommandCount() {
            return sentStartCommands.size();
        }

        StartRecordingCommand lastStartCommand() {
            return sentStartCommands.get(sentStartCommands.size() - 1);
        }

        UUID lastStartRecordingId() {
            return sentStartRecordingIds.get(sentStartRecordingIds.size() - 1);
        }

        int stopCommandCount() {
            return sentStopCommands.size();
        }

        StopRecordingCommand lastStopCommand() {
            return sentStopCommands.get(sentStopCommands.size() - 1);
        }
    }

    private static class ConcurrentDuplicateStartRepository implements RecordingRepository {

        private final UUID violationId;
        private final UUID existingStartCommandId;
        private final Map<UUID, Recording> byViolationId = new HashMap<>();
        private boolean firstCreate = true;

        private ConcurrentDuplicateStartRepository(
                UUID violationId,
                UUID existingStartCommandId
        ) {
            this.violationId = violationId;
            this.existingStartCommandId = existingStartCommandId;
        }

        @Override
        public Optional<Recording> findByViolationId(
                UUID lookupViolationId
        ) {
            return Optional.ofNullable(byViolationId.get(lookupViolationId));
        }

        @Override
        public Recording save(
                Recording recording
        ) {
            if (recording.id() == null && firstCreate) {
                firstCreate = false;
                Recording concurrentInserted = Recording.rehydrate(
                        UUID.randomUUID(),
                        violationId,
                        RecordingStatus.REQUESTED,
                        null,
                        existingStartCommandId,
                        null
                );
                byViolationId.put(violationId, concurrentInserted);
                throw new DataIntegrityViolationException("duplicate violation_id");
            }

            Recording persisted = recording.id() == null
                    ? Recording.rehydrate(
                    UUID.randomUUID(),
                    recording.violationId(),
                    recording.status(),
                    recording.recordingStartedAt(),
                    recording.startCommandId(),
                    recording.stopCommandId()
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
