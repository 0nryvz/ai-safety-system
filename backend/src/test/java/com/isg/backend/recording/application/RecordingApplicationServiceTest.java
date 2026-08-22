package com.isg.backend.recording.application;

import com.isg.backend.recording.application.callback.RecordingStatusCallback;
import com.isg.backend.recording.application.callback.RecordingStatusCallbackPort;
import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.application.port.ViolationClipGroupingContext;
import com.isg.backend.recording.application.port.ViolationClipGroupingPort;
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
    private CapturingRecordingStatusCallbackPort callbackPort;
    private RecordingApplicationService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryRecordingRepository();
        gatewayCommandPort = new CapturingGatewayRecordingCommandPort();
        callbackPort = new CapturingRecordingStatusCallbackPort();
        service = new RecordingApplicationService(
                repository,
                new RecordingCreationService(repository),
                gatewayCommandPort,
                callbackPort
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
                gatewayCommandPort,
                callbackPort
        );

        Recording created = concurrentService.start(startCommand(violationId));

        assertThat(created.violationId()).isEqualTo(violationId);
        assertThat(created.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(created.startCommandId()).isEqualTo(persistedCommandId);
        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(1);
        assertThat(gatewayCommandPort.lastStartCommand().commandId()).isEqualTo(persistedCommandId);
        assertThat(concurrentRepository.size()).isEqualTo(1);
    }

    @Test
    void validReadyCallbackTransitionsAndPublishes() {
        UUID violationId = UUID.randomUUID();
        Recording processing = moveToProcessing(violationId);

        service.handleCallback(readyCallback(
                processing.id(),
                violationId,
                "clips/ready.mp4",
                2_500,
                10_000L,
                "sha256:ready",
                1
        ));

        Recording saved = repository.findById(processing.id()).orElseThrow();
        assertThat(saved.status()).isEqualTo(RecordingStatus.READY);
        assertThat(saved.objectKey()).isEqualTo("clips/ready.mp4");
        assertThat(saved.durationMs()).isEqualTo(2_500);
        assertThat(saved.sizeBytes()).isEqualTo(10_000L);
        assertThat(saved.checksum()).isEqualTo("sha256:ready");
        assertThat(saved.retryCount()).isEqualTo(1);
        assertThat(saved.readyAt()).isNotNull();
        assertThat(callbackPort.publishCount()).isEqualTo(1);
    }

    @Test
    void validErrorCallbackTransitionsAndPublishes() {
        UUID violationId = UUID.randomUUID();
        Recording processing = moveToProcessing(violationId);

        service.handleCallback(errorCallback(
                processing.id(),
                violationId,
                "UPLOAD_FAILED",
                2
        ));

        Recording saved = repository.findById(processing.id()).orElseThrow();
        assertThat(saved.status()).isEqualTo(RecordingStatus.ERROR);
        assertThat(saved.errorCode()).isEqualTo("UPLOAD_FAILED");
        assertThat(saved.retryCount()).isEqualTo(2);
        assertThat(callbackPort.publishCount()).isEqualTo(1);
    }

    @Test
    void duplicateIdenticalReadyIsNoopAndDoesNotRepublish() {
        UUID violationId = UUID.randomUUID();
        Recording processing = moveToProcessing(violationId);
        RecordingCallbackCommand callback = readyCallback(
                processing.id(),
                violationId,
                "clips/ready.mp4",
                2_500,
                10_000L,
                "sha256:ready",
                1
        );

        service.handleCallback(callback);
        service.handleCallback(callback);

        assertThat(callbackPort.publishCount()).isEqualTo(1);
        assertThat(repository.findById(processing.id()).orElseThrow().status())
                .isEqualTo(RecordingStatus.READY);
    }

    @Test
    void conflictingDuplicateReadyThrowsConflict() {
        UUID violationId = UUID.randomUUID();
        Recording processing = moveToProcessing(violationId);

        service.handleCallback(readyCallback(
                processing.id(),
                violationId,
                "clips/ready.mp4",
                2_500,
                10_000L,
                "sha256:ready",
                1
        ));

        assertThatThrownBy(() -> service.handleCallback(readyCallback(
                processing.id(),
                violationId,
                "clips/ready.mp4",
                2_500,
                10_001L,
                "sha256:ready",
                1
        )))
                .isInstanceOf(RecordingCallbackConflictException.class);
    }

    @Test
    void unknownRecordingThrowsNotFound() {
        assertThatThrownBy(() -> service.handleCallback(readyCallback(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "clips/ready.mp4",
                2_500,
                10_000L,
                "sha256:ready",
                0
        )))
                .isInstanceOf(RecordingNotFoundException.class);
    }

    @Test
    void violationMismatchThrowsConflict() {
        UUID violationId = UUID.randomUUID();
        Recording processing = moveToProcessing(violationId);

        assertThatThrownBy(() -> service.handleCallback(readyCallback(
                processing.id(),
                UUID.randomUUID(),
                "clips/ready.mp4",
                2_500,
                10_000L,
                "sha256:ready",
                1
        )))
                .isInstanceOf(RecordingCallbackConflictException.class);
    }

    @Test
    void duplicateIdenticalErrorIsNoopAndDoesNotRepublish() {
        UUID violationId = UUID.randomUUID();
        Recording processing = moveToProcessing(violationId);
        RecordingCallbackCommand callback = errorCallback(
                processing.id(),
                violationId,
                "UPLOAD_FAILED",
                2
        );

        service.handleCallback(callback);
        service.handleCallback(callback);

        assertThat(callbackPort.publishCount()).isEqualTo(1);
        assertThat(repository.findById(processing.id()).orElseThrow().status())
                .isEqualTo(RecordingStatus.ERROR);
    }

    @Test
    void readyThenErrorCallbackThrowsConflict() {
        UUID violationId = UUID.randomUUID();
        Recording processing = moveToProcessing(violationId);

        service.handleCallback(readyCallback(
                processing.id(),
                violationId,
                "clips/ready.mp4",
                2_500,
                10_000L,
                "sha256:ready",
                1
        ));

        assertThatThrownBy(() -> service.handleCallback(errorCallback(
                processing.id(),
                violationId,
                "UPLOAD_FAILED",
                2
        )))
                .isInstanceOf(RecordingCallbackConflictException.class);
    }

    @Test
    void stopAfterEarlyReadyRepublishesReadyStatusWithoutCallingGatewayStop() {
        UUID violationId = UUID.randomUUID();

        Recording recording =
                service.start(
                        startCommand(violationId)
                );

        service.handleCallback(
                readyCallback(
                        recording.id(),
                        violationId,
                        "clips/max-duration.mp4",
                        30_000,
                        500_000L,
                        "sha256:max-duration",
                        0
                )
        );

        assertThat(
                repository.findById(
                        recording.id()
                ).orElseThrow().status()
        ).isEqualTo(
                RecordingStatus.READY
        );

        assertThat(
                callbackPort.publishCount()
        ).isEqualTo(1);

        service.stop(
                stopCommand(violationId)
        );

        assertThat(
                gatewayCommandPort.stopCommandCount()
        ).isEqualTo(0);

        assertThat(
                callbackPort.publishCount()
        ).isEqualTo(2);
    }

    @Test
    void stopAfterEarlyErrorRepublishesErrorStatusWithoutCallingGatewayStop() {
        UUID violationId = UUID.randomUUID();

        Recording recording =
                service.start(
                        startCommand(violationId)
                );

        service.handleCallback(
                errorCallback(
                        recording.id(),
                        violationId,
                        "CLIP_UPLOAD_FAILED",
                        0
                )
        );

        assertThat(
                repository.findById(
                        recording.id()
                ).orElseThrow().status()
        ).isEqualTo(
                RecordingStatus.ERROR
        );

        assertThat(
                callbackPort.publishCount()
        ).isEqualTo(1);

        service.stop(
                stopCommand(violationId)
        );

        assertThat(
                gatewayCommandPort.stopCommandCount()
        ).isEqualTo(0);

        assertThat(
                callbackPort.publishCount()
        ).isEqualTo(2);
    }

    @Test
    void sharedTrackedViolationsUseSinglePhysicalClipAndFanOutReadyCallback() {
        UUID cameraId = UUID.randomUUID();
        UUID cameraSessionId = UUID.randomUUID();

        UUID firstViolationId = UUID.randomUUID();
        UUID secondViolationId = UUID.randomUUID();
        UUID thirdViolationId = UUID.randomUUID();

        ViolationClipGroupingContext context =
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-42"
                );

        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        groupingPort.register(firstViolationId, context);
        groupingPort.register(secondViolationId, context);
        groupingPort.register(thirdViolationId, context);

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        Recording first =
                sharedService.start(
                        startCommand(firstViolationId)
                );

        Recording second =
                sharedService.start(
                        startCommand(secondViolationId)
                );

        Recording third =
                sharedService.start(
                        startCommand(thirdViolationId)
                );

        assertThat(repository.size()).isEqualTo(3);
        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(1);

        assertThat(first.clipGroupId()).isNotNull();
        assertThat(second.clipGroupId()).isEqualTo(first.clipGroupId());
        assertThat(third.clipGroupId()).isEqualTo(first.clipGroupId());

        assertThat(first.startCommandId()).isNotNull();
        assertThat(second.startCommandId()).isNull();
        assertThat(third.startCommandId()).isNull();

        assertThat(first.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(second.status()).isEqualTo(RecordingStatus.RECORDING);
        assertThat(third.status()).isEqualTo(RecordingStatus.RECORDING);

        groupingPort.deactivate(firstViolationId);

        Recording firstStopped =
                sharedService.stop(
                        stopCommand(firstViolationId)
                );

        assertThat(firstStopped.status())
                .isEqualTo(RecordingStatus.PROCESSING);
        assertThat(gatewayCommandPort.stopCommandCount()).isZero();

        groupingPort.deactivate(secondViolationId);

        Recording secondStopped =
                sharedService.stop(
                        stopCommand(secondViolationId)
                );

        assertThat(secondStopped.status())
                .isEqualTo(RecordingStatus.PROCESSING);
        assertThat(gatewayCommandPort.stopCommandCount()).isZero();

        groupingPort.deactivate(thirdViolationId);

        Recording thirdStopped =
                sharedService.stop(
                        stopCommand(thirdViolationId)
                );

        assertThat(thirdStopped.status())
                .isEqualTo(RecordingStatus.PROCESSING);

        assertThat(gatewayCommandPort.stopCommandCount()).isEqualTo(1);
        assertThat(
                gatewayCommandPort
                        .lastStopCommand()
                        .violationId()
        ).isEqualTo(firstViolationId);

        Recording persistedLeader =
                repository
                        .findByViolationId(firstViolationId)
                        .orElseThrow();

        Recording persistedSecond =
                repository
                        .findByViolationId(secondViolationId)
                        .orElseThrow();

        Recording persistedThird =
                repository
                        .findByViolationId(thirdViolationId)
                        .orElseThrow();

        assertThat(persistedLeader.stopCommandId())
                .isEqualTo(
                        gatewayCommandPort
                                .lastStopCommand()
                                .commandId()
                );

        assertThat(persistedSecond.stopCommandId()).isNull();
        assertThat(persistedThird.stopCommandId()).isNull();

        String objectKey =
                "recordings/shared/track-42.mp4";

        String checksum =
                "shared-checksum";

        sharedService.handleCallback(
                readyCallback(
                        persistedLeader.id(),
                        firstViolationId,
                        objectKey,
                        12_000,
                        4_096L,
                        checksum,
                        0
                )
        );

        Recording readyFirst =
                repository
                        .findByViolationId(firstViolationId)
                        .orElseThrow();

        Recording readySecond =
                repository
                        .findByViolationId(secondViolationId)
                        .orElseThrow();

        Recording readyThird =
                repository
                        .findByViolationId(thirdViolationId)
                        .orElseThrow();

        assertThat(readyFirst.status())
                .isEqualTo(RecordingStatus.READY);
        assertThat(readySecond.status())
                .isEqualTo(RecordingStatus.READY);
        assertThat(readyThird.status())
                .isEqualTo(RecordingStatus.READY);

        assertThat(readyFirst.objectKey()).isEqualTo(objectKey);
        assertThat(readySecond.objectKey()).isEqualTo(objectKey);
        assertThat(readyThird.objectKey()).isEqualTo(objectKey);

        assertThat(readyFirst.durationMs()).isEqualTo(12_000);
        assertThat(readySecond.durationMs()).isEqualTo(12_000);
        assertThat(readyThird.durationMs()).isEqualTo(12_000);

        assertThat(readyFirst.sizeBytes()).isEqualTo(4_096L);
        assertThat(readySecond.sizeBytes()).isEqualTo(4_096L);
        assertThat(readyThird.sizeBytes()).isEqualTo(4_096L);

        assertThat(readyFirst.checksum()).isEqualTo(checksum);
        assertThat(readySecond.checksum()).isEqualTo(checksum);
        assertThat(readyThird.checksum()).isEqualTo(checksum);

        assertThat(readyFirst.readyAt()).isNotNull();
        assertThat(readySecond.readyAt())
                .isEqualTo(readyFirst.readyAt());
        assertThat(readyThird.readyAt())
                .isEqualTo(readyFirst.readyAt());

        assertThat(callbackPort.publishCount()).isEqualTo(3);
    }
    @Test
    void sharedErrorCallbackFansOutToAllLogicalRecordings() {
        UUID cameraId = UUID.randomUUID();
        UUID cameraSessionId = UUID.randomUUID();

        UUID firstViolationId = UUID.randomUUID();
        UUID secondViolationId = UUID.randomUUID();

        ViolationClipGroupingContext context =
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-error"
                );

        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        groupingPort.register(firstViolationId, context);
        groupingPort.register(secondViolationId, context);

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        Recording first =
                sharedService.start(
                        startCommand(firstViolationId)
                );

        sharedService.start(
                startCommand(secondViolationId)
        );

        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(1);

        groupingPort.deactivate(firstViolationId);
        sharedService.stop(
                stopCommand(firstViolationId)
        );

        groupingPort.deactivate(secondViolationId);
        sharedService.stop(
                stopCommand(secondViolationId)
        );

        assertThat(gatewayCommandPort.stopCommandCount()).isEqualTo(1);

        Recording persistedLeader =
                repository
                        .findByViolationId(firstViolationId)
                        .orElseThrow();

        sharedService.handleCallback(
                errorCallback(
                        persistedLeader.id(),
                        firstViolationId,
                        "ENCODE_FAILED",
                        2
                )
        );

        Recording erroredFirst =
                repository
                        .findByViolationId(firstViolationId)
                        .orElseThrow();

        Recording erroredSecond =
                repository
                        .findByViolationId(secondViolationId)
                        .orElseThrow();

        assertThat(erroredFirst.status())
                .isEqualTo(RecordingStatus.ERROR);

        assertThat(erroredSecond.status())
                .isEqualTo(RecordingStatus.ERROR);

        assertThat(erroredFirst.errorCode())
                .isEqualTo("ENCODE_FAILED");

        assertThat(erroredSecond.errorCode())
                .isEqualTo("ENCODE_FAILED");

        assertThat(erroredFirst.retryCount()).isEqualTo(2);
        assertThat(erroredSecond.retryCount()).isEqualTo(2);

        assertThat(callbackPort.publishCount()).isEqualTo(2);
    }

    @Test
    void sharedFailedPhysicalStopRetryUsesPersistedStopCommandId() {
        UUID cameraId = UUID.randomUUID();
        UUID cameraSessionId = UUID.randomUUID();

        UUID leaderViolationId = UUID.randomUUID();
        UUID followerViolationId = UUID.randomUUID();

        ViolationClipGroupingContext context =
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-stop-retry"
                );

        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        groupingPort.register(leaderViolationId, context);
        groupingPort.register(followerViolationId, context);

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        sharedService.start(
                startCommand(leaderViolationId)
        );

        sharedService.start(
                startCommand(followerViolationId)
        );

        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(1);

        groupingPort.deactivate(leaderViolationId);

        sharedService.stop(
                stopCommand(leaderViolationId)
        );

        assertThat(gatewayCommandPort.stopCommandCount()).isZero();

        groupingPort.deactivate(followerViolationId);

        StopRecordingCommand firstStopAttempt =
                stopCommand(followerViolationId);

        gatewayCommandPort.failStop = true;

        assertThatThrownBy(
                () -> sharedService.stop(firstStopAttempt)
        ).isInstanceOf(RuntimeException.class)
                .hasMessage("stop failed");

        Recording leaderAfterFailure =
                repository
                        .findByViolationId(leaderViolationId)
                        .orElseThrow();

        Recording followerAfterFailure =
                repository
                        .findByViolationId(followerViolationId)
                        .orElseThrow();

        assertThat(leaderAfterFailure.stopCommandId())
                .isEqualTo(firstStopAttempt.commandId());

        assertThat(followerAfterFailure.status())
                .isEqualTo(RecordingStatus.RECORDING);

        gatewayCommandPort.failStop = false;

        StopRecordingCommand retryEvent =
                stopCommand(followerViolationId);

        Recording stopped =
                sharedService.stop(retryEvent);

        assertThat(stopped.status())
                .isEqualTo(RecordingStatus.PROCESSING);

        assertThat(gatewayCommandPort.stopCommandCount()).isEqualTo(1);

        assertThat(
                gatewayCommandPort
                        .lastStopCommand()
                        .commandId()
        ).isEqualTo(firstStopAttempt.commandId());

        assertThat(
                gatewayCommandPort
                        .lastStopCommand()
                        .commandId()
        ).isNotEqualTo(retryEvent.commandId());

        assertThat(
                gatewayCommandPort
                        .lastStopCommand()
                        .violationId()
        ).isEqualTo(leaderViolationId);

        Recording persistedLeader =
                repository
                        .findByViolationId(leaderViolationId)
                        .orElseThrow();

        assertThat(persistedLeader.stopCommandId())
                .isEqualTo(firstStopAttempt.commandId());
    }
    @Test
    void violationsWithoutStableGroupingContextRemainSeparate() {
        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        Recording first =
                sharedService.start(
                        startCommand(UUID.randomUUID())
                );

        Recording second =
                sharedService.start(
                        startCommand(UUID.randomUUID())
                );

        assertThat(first.clipGroupId()).isNull();
        assertThat(second.clipGroupId()).isNull();
        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(2);
    }

    @Test
    void sharedFailedStartRetryUsesPersistedStartCommandIdAndPromotesFollower() {
        UUID cameraId = UUID.randomUUID();
        UUID cameraSessionId = UUID.randomUUID();
        UUID leaderViolationId = UUID.randomUUID();
        UUID followerViolationId = UUID.randomUUID();

        ViolationClipGroupingContext context =
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-start-retry"
                );

        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        groupingPort.register(leaderViolationId, context);
        groupingPort.register(followerViolationId, context);

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        StartRecordingCommand firstCommand =
                startCommand(leaderViolationId);

        gatewayCommandPort.failStart = true;

        assertThatThrownBy(
                () -> sharedService.start(firstCommand)
        ).isInstanceOf(RuntimeException.class)
                .hasMessage("start failed");

        Recording persistedLeader =
                repository
                        .findByViolationId(leaderViolationId)
                        .orElseThrow();

        assertThat(persistedLeader.status())
                .isEqualTo(RecordingStatus.REQUESTED);

        assertThat(persistedLeader.startCommandId())
                .isEqualTo(firstCommand.commandId());

        Recording waitingFollower =
                sharedService.start(
                        startCommand(followerViolationId)
                );

        assertThat(waitingFollower.status())
                .isEqualTo(RecordingStatus.REQUESTED);

        assertThat(waitingFollower.startCommandId()).isNull();

        gatewayCommandPort.failStart = false;

        StartRecordingCommand retryEvent =
                startCommand(leaderViolationId);

        Recording startedLeader =
                sharedService.start(retryEvent);

        Recording startedFollower =
                repository
                        .findByViolationId(followerViolationId)
                        .orElseThrow();

        assertThat(startedLeader.startCommandId())
                .isEqualTo(firstCommand.commandId());

        assertThat(
                gatewayCommandPort
                        .lastStartCommand()
                        .commandId()
        ).isEqualTo(firstCommand.commandId());

        assertThat(
                gatewayCommandPort
                        .lastStartCommand()
                        .commandId()
        ).isNotEqualTo(retryEvent.commandId());

        assertThat(gatewayCommandPort.startCommandCount())
                .isEqualTo(1);

        assertThat(startedFollower.status())
                .isEqualTo(RecordingStatus.RECORDING);

        assertThat(startedFollower.startCommandId()).isNull();

        assertThat(startedFollower.clipGroupId())
                .isEqualTo(startedLeader.clipGroupId());
    }

    @Test
    void duplicateSharedReadyCallbackDoesNotRepublishLogicalCallbacks() {
        UUID cameraId = UUID.randomUUID();
        UUID cameraSessionId = UUID.randomUUID();
        UUID firstViolationId = UUID.randomUUID();
        UUID secondViolationId = UUID.randomUUID();

        ViolationClipGroupingContext context =
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-ready-idempotent"
                );

        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        groupingPort.register(firstViolationId, context);
        groupingPort.register(secondViolationId, context);

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        sharedService.start(startCommand(firstViolationId));
        sharedService.start(startCommand(secondViolationId));

        groupingPort.deactivate(firstViolationId);
        sharedService.stop(stopCommand(firstViolationId));

        groupingPort.deactivate(secondViolationId);
        sharedService.stop(stopCommand(secondViolationId));

        Recording leader =
                repository
                        .findByViolationId(firstViolationId)
                        .orElseThrow();

        RecordingCallbackCommand callback =
                readyCallback(
                        leader.id(),
                        firstViolationId,
                        "recordings/shared/idempotent.mp4",
                        9_000,
                        2_048L,
                        "same-checksum",
                        0
                );

        sharedService.handleCallback(callback);
        sharedService.handleCallback(callback);

        assertThat(callbackPort.publishCount()).isEqualTo(2);

        assertThat(
                repository
                        .findByViolationId(firstViolationId)
                        .orElseThrow()
                        .status()
        ).isEqualTo(RecordingStatus.READY);

        assertThat(
                repository
                        .findByViolationId(secondViolationId)
                        .orElseThrow()
                        .status()
        ).isEqualTo(RecordingStatus.READY);
    }

    @Test
    void sharedConcurrentStartsCreateOnlyOnePhysicalStart() throws Exception {
        UUID cameraId = UUID.randomUUID();
        UUID cameraSessionId = UUID.randomUUID();

        UUID firstViolationId = UUID.randomUUID();
        UUID secondViolationId = UUID.randomUUID();
        UUID thirdViolationId = UUID.randomUUID();

        ViolationClipGroupingContext context =
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-concurrent"
                );

        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        groupingPort.register(firstViolationId, context);
        groupingPort.register(secondViolationId, context);
        groupingPort.register(thirdViolationId, context);

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        StartRecordingCommand firstCommand =
                startCommand(firstViolationId);

        StartRecordingCommand secondCommand =
                startCommand(secondViolationId);

        StartRecordingCommand thirdCommand =
                startCommand(thirdViolationId);

        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(3);

        try {
            java.util.List<java.util.concurrent.Future<Recording>> futures =
                    executor.invokeAll(
                            java.util.List.of(
                                    () -> sharedService.start(firstCommand),
                                    () -> sharedService.start(secondCommand),
                                    () -> sharedService.start(thirdCommand)
                            )
                    );

            for (java.util.concurrent.Future<Recording> future : futures) {
                assertThat(future.get().status())
                        .isEqualTo(RecordingStatus.RECORDING);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(repository.size()).isEqualTo(3);
        assertThat(gatewayCommandPort.startCommandCount()).isEqualTo(1);

        Recording first =
                repository.findByViolationId(firstViolationId).orElseThrow();

        Recording second =
                repository.findByViolationId(secondViolationId).orElseThrow();

        Recording third =
                repository.findByViolationId(thirdViolationId).orElseThrow();

        assertThat(first.clipGroupId()).isNotNull();
        assertThat(second.clipGroupId()).isEqualTo(first.clipGroupId());
        assertThat(third.clipGroupId()).isEqualTo(first.clipGroupId());

        long physicalOwners =
                java.util.List.of(first, second, third)
                        .stream()
                        .filter(recording -> recording.startCommandId() != null)
                        .count();

        assertThat(physicalOwners).isEqualTo(1);
    }

    @Test
    void newViolationAfterSharedClipStopStartsNewPhysicalGroup() {
        UUID cameraId = UUID.randomUUID();
        UUID cameraSessionId = UUID.randomUUID();

        UUID firstViolationId = UUID.randomUUID();
        UUID secondViolationId = UUID.randomUUID();
        UUID newViolationId = UUID.randomUUID();

        ViolationClipGroupingContext context =
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-reopen"
                );

        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        groupingPort.register(firstViolationId, context);
        groupingPort.register(secondViolationId, context);

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        Recording first =
                sharedService.start(
                        startCommand(firstViolationId)
                );

        sharedService.start(
                startCommand(secondViolationId)
        );

        UUID oldClipGroupId = first.clipGroupId();

        groupingPort.deactivate(firstViolationId);
        sharedService.stop(stopCommand(firstViolationId));

        groupingPort.deactivate(secondViolationId);
        sharedService.stop(stopCommand(secondViolationId));

        assertThat(gatewayCommandPort.stopCommandCount()).isEqualTo(1);

        groupingPort.register(newViolationId, context);

        Recording reopened =
                sharedService.start(
                        startCommand(newViolationId)
                );

        assertThat(reopened.clipGroupId()).isNotNull();
        assertThat(reopened.clipGroupId())
                .isNotEqualTo(oldClipGroupId);

        assertThat(reopened.startCommandId()).isNotNull();

        assertThat(gatewayCommandPort.startCommandCount())
                .isEqualTo(2);
    }
    @Test
    void differentTrackedSubjectsCreateDifferentPhysicalGroups() {
        UUID cameraId = UUID.randomUUID();
        UUID cameraSessionId = UUID.randomUUID();

        UUID firstViolationId = UUID.randomUUID();
        UUID secondViolationId = UUID.randomUUID();

        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        groupingPort.register(
                firstViolationId,
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-101"
                )
        );

        groupingPort.register(
                secondViolationId,
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-202"
                )
        );

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        Recording first =
                sharedService.start(
                        startCommand(firstViolationId)
                );

        Recording second =
                sharedService.start(
                        startCommand(secondViolationId)
                );

        assertThat(first.clipGroupId()).isNotNull();
        assertThat(second.clipGroupId()).isNotNull();

        assertThat(second.clipGroupId())
                .isNotEqualTo(first.clipGroupId());

        assertThat(first.startCommandId()).isNotNull();
        assertThat(second.startCommandId()).isNotNull();

        assertThat(gatewayCommandPort.startCommandCount())
                .isEqualTo(2);
    }

    @Test
    void duplicateSharedErrorCallbackDoesNotRepublishLogicalCallbacks() {
        UUID cameraId = UUID.randomUUID();
        UUID cameraSessionId = UUID.randomUUID();

        UUID firstViolationId = UUID.randomUUID();
        UUID secondViolationId = UUID.randomUUID();

        ViolationClipGroupingContext context =
                new ViolationClipGroupingContext(
                        cameraId,
                        cameraSessionId,
                        "track-error-idempotent"
                );

        FakeViolationClipGroupingPort groupingPort =
                new FakeViolationClipGroupingPort();

        groupingPort.register(firstViolationId, context);
        groupingPort.register(secondViolationId, context);

        RecordingApplicationService sharedService =
                new RecordingApplicationService(
                        repository,
                        new RecordingCreationService(repository),
                        gatewayCommandPort,
                        callbackPort,
                        groupingPort,
                        new RecordingCommandStateService(repository)
                );

        sharedService.start(
                startCommand(firstViolationId)
        );

        sharedService.start(
                startCommand(secondViolationId)
        );

        groupingPort.deactivate(firstViolationId);

        sharedService.stop(
                stopCommand(firstViolationId)
        );

        groupingPort.deactivate(secondViolationId);

        sharedService.stop(
                stopCommand(secondViolationId)
        );

        Recording leader =
                repository
                        .findByViolationId(firstViolationId)
                        .orElseThrow();

        RecordingCallbackCommand callback =
                new RecordingCallbackCommand(
                        leader.id(),
                        leader.violationId(),
                        RecordingStatus.ERROR,
                        null,
                        null,
                        null,
                        null,
                        0,
                        "ENCODE_FAILED"
                );

        sharedService.handleCallback(callback);
        sharedService.handleCallback(callback);

        assertThat(callbackPort.publishCount())
                .isEqualTo(2);

        assertThat(
                repository
                        .findByViolationId(firstViolationId)
                        .orElseThrow()
                        .status()
        ).isEqualTo(RecordingStatus.ERROR);

        assertThat(
                repository
                        .findByViolationId(secondViolationId)
                        .orElseThrow()
                        .status()
        ).isEqualTo(RecordingStatus.ERROR);
    }
    private Recording moveToProcessing(
            UUID violationId
    ) {
        service.start(startCommand(violationId));
        return service.stop(stopCommand(violationId));
    }

    private RecordingCallbackCommand readyCallback(
            UUID recordingId,
            UUID violationId,
            String objectKey,
            Integer durationMs,
            Long sizeBytes,
            String checksum,
            Integer retryCount
    ) {
        return new RecordingCallbackCommand(
                recordingId,
                violationId,
                RecordingStatus.READY,
                objectKey,
                durationMs,
                sizeBytes,
                checksum,
                retryCount,
                null
        );
    }

    private RecordingCallbackCommand errorCallback(
            UUID recordingId,
            UUID violationId,
            String errorCode,
            Integer retryCount
    ) {
        return new RecordingCallbackCommand(
                recordingId,
                violationId,
                RecordingStatus.ERROR,
                null,
                null,
                null,
                null,
                retryCount,
                errorCode
        );
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

    private static class FakeViolationClipGroupingPort
            implements ViolationClipGroupingPort {

        private final Map<UUID, ViolationClipGroupingContext> contexts =
                new HashMap<>();

        private final Set<UUID> activeViolationIds =
                new HashSet<>();

        void register(
                UUID violationId,
                ViolationClipGroupingContext context
        ) {
            contexts.put(
                    violationId,
                    context
            );
            activeViolationIds.add(
                    violationId
            );
        }

        void deactivate(
                UUID violationId
        ) {
            activeViolationIds.remove(
                    violationId
            );
        }

        @Override
        public Optional<ViolationClipGroupingContext> findContext(
                UUID violationId
        ) {
            return Optional.ofNullable(
                    contexts.get(violationId)
            );
        }

        @Override
        public Set<UUID> findActiveViolationIds(
                ViolationClipGroupingContext context
        ) {
            Set<UUID> result =
                    new HashSet<>();

            for (UUID violationId : activeViolationIds) {
                if (Objects.equals(
                        contexts.get(violationId),
                        context
                )) {
                    result.add(violationId);
                }
            }

            return Set.copyOf(result);
        }
    }
    private static class InMemoryRecordingRepository implements RecordingRepository {

        private final Map<UUID, Recording> byViolationId = new HashMap<>();

        @Override
        public Optional<Recording> findById(
                UUID recordingId
        ) {
            return byViolationId.values().stream()
                    .filter(recording -> recording.id().equals(recordingId))
                    .findFirst();
        }

        @Override
        public Optional<Recording> findByViolationId(
                UUID violationId
        ) {
            return Optional.ofNullable(byViolationId.get(violationId));
        }

        @Override
        public Map<UUID, Recording> findByViolationIds(
                Collection<UUID> violationIds
        ) {
            return violationIds.stream()
                    .filter(byViolationId::containsKey)
                    .collect(
                            java.util.stream.Collectors.toMap(
                                    violationId -> violationId,
                                    byViolationId::get
                            )
                    );
        }

        @Override
        public List<Recording> findByClipGroupId(
                UUID clipGroupId
        ) {
            return byViolationId.values().stream()
                    .filter(recording -> Objects.equals(recording.clipGroupId(), clipGroupId))
                    .toList();
        }

        @Override
        public Set<UUID> findViolationIdsByStatus(
                RecordingStatus status
        ) {
            return byViolationId.values().stream()
                    .filter(
                            recording ->
                                    recording.status() == status
                    )
                    .map(Recording::violationId)
                    .collect(
                            java.util.stream.Collectors.toSet()
                    );
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
                    recording.objectKey(),
                    recording.durationMs(),
                    recording.sizeBytes(),
                    recording.retryCount(),
                    recording.checksum(),
                    recording.errorCode(),
                    recording.recordingStartedAt(),
                    recording.startCommandId(),
                    recording.stopCommandId(),
                    recording.clipGroupId(),
                    recording.readyAt()
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
        public Optional<Recording> findById(
                UUID recordingId
        ) {
            return byViolationId.values().stream()
                    .filter(recording -> recording.id().equals(recordingId))
                    .findFirst();
        }

        @Override
        public Map<UUID, Recording> findByViolationIds(
                Collection<UUID> violationIds
        ) {
            return violationIds.stream()
                    .filter(byViolationId::containsKey)
                    .collect(
                            java.util.stream.Collectors.toMap(
                                    violationId -> violationId,
                                    byViolationId::get
                            )
                    );
        }

        @Override
        public List<Recording> findByClipGroupId(
                UUID clipGroupId
        ) {
            return byViolationId.values().stream()
                    .filter(recording -> Objects.equals(recording.clipGroupId(), clipGroupId))
                    .toList();
        }

        @Override
        public Set<UUID> findViolationIdsByStatus(
                RecordingStatus status
        ) {
            return byViolationId.values().stream()
                    .filter(
                            recording ->
                                    recording.status() == status
                    )
                    .map(Recording::violationId)
                    .collect(
                            java.util.stream.Collectors.toSet()
                    );
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
                    recording.objectKey(),
                    recording.durationMs(),
                    recording.sizeBytes(),
                    recording.retryCount(),
                    recording.checksum(),
                    recording.errorCode(),
                    recording.recordingStartedAt(),
                    recording.startCommandId(),
                    recording.stopCommandId(),
                    recording.clipGroupId(),
                    recording.readyAt()
            )
                    : recording;

            byViolationId.put(persisted.violationId(), persisted);
            return persisted;
        }

        int size() {
            return byViolationId.size();
        }
    }

    private static class CapturingRecordingStatusCallbackPort implements RecordingStatusCallbackPort {

        private final List<RecordingStatusCallback> callbacks = new ArrayList<>();

        @Override
        public void publish(
                RecordingStatusCallback callback
        ) {
            callbacks.add(callback);
        }

        int publishCount() {
            return callbacks.size();
        }
    }
}
