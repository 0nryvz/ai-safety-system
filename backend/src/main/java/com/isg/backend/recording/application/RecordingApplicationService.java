package com.isg.backend.recording.application;

import com.isg.backend.recording.application.callback.RecordingStatusCallback;
import com.isg.backend.recording.application.callback.RecordingStatusCallbackPort;
import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import com.isg.backend.recording.application.port.RecordingRepository;
import com.isg.backend.recording.application.port.ViolationClipGroupingPort;
import com.isg.backend.recording.application.port.ViolationClipGroupingContext;
import com.isg.backend.recording.domain.Recording;
import com.isg.backend.recording.domain.RecordingStatus;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class RecordingApplicationService {

    private final RecordingRepository recordingRepository;
    private final RecordingCreationService recordingCreationService;
    private final GatewayRecordingCommandPort gatewayRecordingCommandPort;
    private final RecordingStatusCallbackPort recordingStatusCallbackPort;
    private final ViolationClipGroupingPort violationClipGroupingPort;
    private final RecordingCommandStateService recordingCommandStateService;
    private final Clock clock;
    private final Object sharedClipLock = new Object();

    public RecordingApplicationService(
            RecordingRepository recordingRepository,
            RecordingCreationService recordingCreationService,
            GatewayRecordingCommandPort gatewayRecordingCommandPort,
            RecordingStatusCallbackPort recordingStatusCallbackPort
    ) {
        this(
                recordingRepository,
                recordingCreationService,
                gatewayRecordingCommandPort,
                recordingStatusCallbackPort,
                null,
                null,
                Clock.systemUTC()
        );
    }

    @Autowired
    public RecordingApplicationService(
            RecordingRepository recordingRepository,
            RecordingCreationService recordingCreationService,
            GatewayRecordingCommandPort gatewayRecordingCommandPort,
            RecordingStatusCallbackPort recordingStatusCallbackPort,
            ViolationClipGroupingPort violationClipGroupingPort,
            RecordingCommandStateService recordingCommandStateService
    ) {
        this(
                recordingRepository,
                recordingCreationService,
                gatewayRecordingCommandPort,
                recordingStatusCallbackPort,
                violationClipGroupingPort,
                recordingCommandStateService,
                Clock.systemUTC()
        );
    }

    RecordingApplicationService(
            RecordingRepository recordingRepository,
            RecordingCreationService recordingCreationService,
            GatewayRecordingCommandPort gatewayRecordingCommandPort,
            RecordingStatusCallbackPort recordingStatusCallbackPort,
            Clock clock
    ) {
        this(
                recordingRepository,
                recordingCreationService,
                gatewayRecordingCommandPort,
                recordingStatusCallbackPort,
                null,
                null,
                clock
        );
    }

    RecordingApplicationService(
            RecordingRepository recordingRepository,
            RecordingCreationService recordingCreationService,
            GatewayRecordingCommandPort gatewayRecordingCommandPort,
            RecordingStatusCallbackPort recordingStatusCallbackPort,
            ViolationClipGroupingPort violationClipGroupingPort,
            Clock clock
    ) {
        this(
                recordingRepository,
                recordingCreationService,
                gatewayRecordingCommandPort,
                recordingStatusCallbackPort,
                violationClipGroupingPort,
                new RecordingCommandStateService(recordingRepository),
                clock
        );
    }

    RecordingApplicationService(
            RecordingRepository recordingRepository,
            RecordingCreationService recordingCreationService,
            GatewayRecordingCommandPort gatewayRecordingCommandPort,
            RecordingStatusCallbackPort recordingStatusCallbackPort,
            ViolationClipGroupingPort violationClipGroupingPort,
            RecordingCommandStateService recordingCommandStateService,
            Clock clock
    ) {
        this.recordingRepository = recordingRepository;
        this.recordingCreationService = recordingCreationService;
        this.gatewayRecordingCommandPort = gatewayRecordingCommandPort;
        this.recordingStatusCallbackPort = recordingStatusCallbackPort;
        this.violationClipGroupingPort = violationClipGroupingPort;
        this.recordingCommandStateService = recordingCommandStateService;
        this.clock = clock;
    }
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recording start(
            StartRecordingCommand command
    ) {
        if (violationClipGroupingPort == null) {
            return startLegacy(command);
        }

        ViolationClipGroupingContext context =
                violationClipGroupingPort
                        .findContext(command.violationId())
                        .orElse(null);

        if (context == null) {
            return startLegacy(command);
        }

        synchronized (sharedClipLock) {
            return startShared(command, context);
        }
    }

    private Recording startLegacy(
            StartRecordingCommand command
    ) {
        Recording recording =
                recordingRepository
                        .findByViolationId(command.violationId())
                        .orElseGet(
                                () -> createRequestedRecording(command)
                        );

        if (recording.status() != RecordingStatus.REQUESTED) {
            return recording;
        }

        UUID startCommandId =
                recording.startCommandId() == null
                        ? command.commandId()
                        : recording.startCommandId();

        StartRecordingCommand startCommand =
                withStartCommandId(
                        command,
                        startCommandId
                );

        gatewayRecordingCommandPort.sendStart(
                recording.id(),
                startCommand
        );

        recording.markRecordingStarted(
                startCommand.startedAt(),
                startCommandId
        );

        return recordingRepository.save(recording);
    }

    private Recording startShared(
            StartRecordingCommand command,
            ViolationClipGroupingContext context
    ) {
        Recording existing =
                recordingRepository
                        .findByViolationId(command.violationId())
                        .orElse(null);

        if (existing != null) {
            return resumeSharedStart(
                    existing,
                    command
            );
        }

        UUID openGroupId =
                findOpenClipGroupId(context);

        if (openGroupId == null) {
            Recording leader =
                    createSharedLeaderRecording(
                            command,
                            UUID.randomUUID()
                    );

            return deliverSharedLeaderStart(
                    leader,
                    command
            );
        }

        Recording follower =
                createSharedFollowerRecording(
                        command.violationId(),
                        openGroupId
                );

        return promoteFollowerIfPhysicalRecordingStarted(
                follower
        );
    }

    private Recording resumeSharedStart(
            Recording recording,
            StartRecordingCommand command
    ) {
        if (recording.status() != RecordingStatus.REQUESTED) {
            return recording;
        }

        if (recording.clipGroupId() == null) {
            return startLegacy(command);
        }

        if (recording.startCommandId() != null) {
            return deliverSharedLeaderStart(
                    recording,
                    command
            );
        }

        return promoteFollowerIfPhysicalRecordingStarted(
                recording
        );
    }

    private UUID findOpenClipGroupId(
            ViolationClipGroupingContext context
    ) {
        UUID openGroupId = null;

        for (Recording candidate :
                recordingRepository
                        .findByViolationIds(
                                violationClipGroupingPort
                                        .findActiveViolationIds(context)
                        )
                        .values()) {

            if (candidate.clipGroupId() == null) {
                continue;
            }

            if (candidate.status() != RecordingStatus.REQUESTED
                    && candidate.status() != RecordingStatus.RECORDING) {
                continue;
            }

            if (openGroupId == null) {
                openGroupId = candidate.clipGroupId();
                continue;
            }

            if (!openGroupId.equals(candidate.clipGroupId())) {
                throw new IllegalStateException(
                        "Multiple open clip groups found for the same violation context"
                );
            }
        }

        return openGroupId;
    }

    private Recording deliverSharedLeaderStart(
            Recording leader,
            StartRecordingCommand command
    ) {
        UUID startCommandId =
                leader.startCommandId() == null
                        ? command.commandId()
                        : leader.startCommandId();

        StartRecordingCommand startCommand =
                withStartCommandId(
                        command,
                        startCommandId
                );

        gatewayRecordingCommandPort.sendStart(
                leader.id(),
                startCommand
        );

        leader.markRecordingStarted(
                startCommand.startedAt(),
                startCommandId
        );

        Recording savedLeader =
                recordingRepository.save(leader);

        promoteWaitingSharedFollowers(
                savedLeader.clipGroupId(),
                savedLeader.recordingStartedAt(),
                savedLeader.violationId()
        );

        return savedLeader;
    }

    private Recording promoteFollowerIfPhysicalRecordingStarted(
            Recording follower
    ) {
        for (Recording member :
                recordingRepository.findByClipGroupId(
                        follower.clipGroupId()
                )) {

            if (member.status() != RecordingStatus.RECORDING) {
                continue;
            }

            Instant startedAt =
                    Objects.requireNonNull(
                            member.recordingStartedAt(),
                            "Shared recordingStartedAt cannot be null"
                    );

            follower.markSharedRecordingStarted(startedAt);
            return recordingRepository.save(follower);
        }

        return follower;
    }

    private void promoteWaitingSharedFollowers(
            UUID clipGroupId,
            Instant startedAt,
            UUID leaderViolationId
    ) {
        for (Recording member :
                recordingRepository.findByClipGroupId(
                        clipGroupId
                )) {

            if (member.violationId().equals(leaderViolationId)
                    || member.status() != RecordingStatus.REQUESTED) {
                continue;
            }

            if (member.startCommandId() != null) {
                throw new IllegalStateException(
                        "Shared clip group contains multiple physical START owners"
                );
            }

            member.markSharedRecordingStarted(startedAt);
            recordingRepository.save(member);
        }
    }

    private Recording createSharedLeaderRecording(
            StartRecordingCommand command,
            UUID clipGroupId
    ) {
        try {
            return recordingCreationService.createRequested(
                    command.violationId(),
                    command.commandId(),
                    clipGroupId
            );
        } catch (DataIntegrityViolationException ex) {
            return recordingRepository
                    .findByViolationId(command.violationId())
                    .orElseThrow(() -> ex);
        }
    }

    private Recording createSharedFollowerRecording(
            UUID violationId,
            UUID clipGroupId
    ) {
        try {
            return recordingCreationService.createSharedRequested(
                    violationId,
                    clipGroupId
            );
        } catch (DataIntegrityViolationException ex) {
            return recordingRepository
                    .findByViolationId(violationId)
                    .orElseThrow(() -> ex);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Recording stop(
            StopRecordingCommand command
    ) {
        Recording recording =
                recordingRepository
                        .findByViolationId(command.violationId())
                        .orElseThrow(
                                () -> new RecordingNotFoundForViolationException(
                                        command.violationId()
                                )
                        );

        if (recording.clipGroupId() == null) {
            return stopLegacy(recording, command);
        }

        if (violationClipGroupingPort == null
                || recordingCommandStateService == null) {
            throw new IllegalStateException(
                    "Shared clip STOP dependencies are unavailable"
            );
        }

        synchronized (sharedClipLock) {
            Recording current =
                    recordingRepository
                            .findByViolationId(command.violationId())
                            .orElseThrow(
                                    () -> new RecordingNotFoundForViolationException(
                                            command.violationId()
                                    )
                            );

            return stopShared(current, command);
        }
    }

    private Recording stopLegacy(
            Recording recording,
            StopRecordingCommand command
    ) {
        if (recording.stopAlreadyHandled()) {
            if (recording.status() == RecordingStatus.READY
                    || recording.status() == RecordingStatus.ERROR) {
                recordingStatusCallbackPort.publish(
                        toStatusCallback(recording)
                );
            }

            return recording;
        }

        if (recording.status() != RecordingStatus.RECORDING) {
            return recording;
        }

        if (recording.stopCommandId() == null) {
            recording.assignStopCommandId(command.commandId());
            recording = recordingRepository.save(recording);
        }

        StopRecordingCommand stopCommand =
                withStopCommandId(
                        command,
                        recording.stopCommandId()
                );

        gatewayRecordingCommandPort.sendStop(stopCommand);
        recording.markProcessing(stopCommand.commandId());

        return recordingRepository.save(recording);
    }

    private Recording stopShared(
            Recording recording,
            StopRecordingCommand command
    ) {
        if (recording.stopAlreadyHandled()) {
            if (recording.status() == RecordingStatus.READY
                    || recording.status() == RecordingStatus.ERROR) {
                recordingStatusCallbackPort.publish(
                        toStatusCallback(recording)
                );
            }

            return recording;
        }

        if (recording.status() != RecordingStatus.RECORDING) {
            return recording;
        }

        ViolationClipGroupingContext context =
                violationClipGroupingPort
                        .findContext(recording.violationId())
                        .orElseThrow(
                                () -> new IllegalStateException(
                                        "Shared clip grouping context not found for violationId="
                                                + recording.violationId()
                                )
                        );

        if (!violationClipGroupingPort
                .findActiveViolationIds(context)
                .isEmpty()) {

            recording.markSharedProcessing();
            return recordingRepository.save(recording);
        }

        Recording physicalOwner =
                findPhysicalStartOwner(
                        recording.clipGroupId()
                );

        Recording claimedOwner =
                recordingCommandStateService.claimStopCommand(
                        physicalOwner.id(),
                        command.commandId()
                );

        UUID physicalStopCommandId =
                Objects.requireNonNull(
                        claimedOwner.stopCommandId(),
                        "Physical stopCommandId cannot be null"
                );

        StopRecordingCommand physicalStopCommand =
                new StopRecordingCommand(
                        physicalStopCommandId,
                        claimedOwner.violationId(),
                        command.endedAt()
                );

        gatewayRecordingCommandPort.sendStop(
                physicalStopCommand
        );

        if (claimedOwner.status() == RecordingStatus.RECORDING) {
            claimedOwner.markProcessing(
                    physicalStopCommandId
            );
            claimedOwner =
                    recordingRepository.save(claimedOwner);
        } else if (claimedOwner.status() != RecordingStatus.PROCESSING) {
            throw new IllegalStateException(
                    "Physical START owner cannot receive STOP from status "
                            + claimedOwner.status()
            );
        }

        if (recording.id().equals(claimedOwner.id())) {
            return claimedOwner;
        }

        recording.markSharedProcessing();
        return recordingRepository.save(recording);
    }

    private Recording findPhysicalStartOwner(
            UUID clipGroupId
    ) {
        Recording owner = null;

        for (Recording member :
                recordingRepository.findByClipGroupId(
                        clipGroupId
                )) {

            if (member.startCommandId() == null) {
                continue;
            }

            if (owner != null
                    && !owner.id().equals(member.id())) {
                throw new IllegalStateException(
                        "Shared clip group contains multiple physical START owners"
                );
            }

            owner = member;
        }

        if (owner == null) {
            throw new IllegalStateException(
                    "Shared clip group has no physical START owner"
            );
        }

        return owner;
    }
    @Transactional
    public void handleCallback(
            RecordingCallbackCommand callback
    ) {
        Recording recording =
                recordingRepository.findById(callback.recordingId())
                        .orElseThrow(
                                () -> new RecordingNotFoundException(
                                        callback.recordingId()
                                )
                        );

        if (!recording.violationId().equals(callback.violationId())) {
            throw new RecordingCallbackConflictException(
                    "Violation mismatch for recordingId="
                            + callback.recordingId()
            );
        }

        if (recording.clipGroupId() != null) {
            handleSharedCallback(
                    recording,
                    callback
            );
            return;
        }

        if (callback.status() == RecordingStatus.READY) {
            handleReadyCallback(recording, callback);
            return;
        }

        if (callback.status() == RecordingStatus.ERROR) {
            handleErrorCallback(recording, callback);
            return;
        }

        throw new RecordingCallbackConflictException(
                "Unsupported callback status=" + callback.status()
        );
    }

    private void handleSharedCallback(
            Recording callbackRecording,
            RecordingCallbackCommand callback
    ) {
        Recording physicalOwner =
                findPhysicalStartOwner(
                        callbackRecording.clipGroupId()
                );

        if (!physicalOwner.id().equals(callbackRecording.id())) {
            throw new RecordingCallbackConflictException(
                    "Shared callback must target physical START owner"
            );
        }

        if (callback.status() == RecordingStatus.READY) {
            handleSharedReadyCallback(
                    callbackRecording.clipGroupId(),
                    callback
            );
            return;
        }

        if (callback.status() == RecordingStatus.ERROR) {
            handleSharedErrorCallback(
                    callbackRecording.clipGroupId(),
                    callback
            );
            return;
        }

        throw new RecordingCallbackConflictException(
                "Unsupported shared callback status="
                        + callback.status()
        );
    }

    private void handleSharedReadyCallback(
            UUID clipGroupId,
            RecordingCallbackCommand callback
    ) {
        int durationMs =
                requirePositive(
                        callback.durationMs(),
                        "durationMs"
                );

        long sizeBytes =
                requirePositive(
                        callback.sizeBytes(),
                        "sizeBytes"
                );

        Instant readyAt = Instant.now(clock);

        for (Recording member :
                recordingRepository.findByClipGroupId(
                        clipGroupId
                )) {

            if (member.status() == RecordingStatus.READY) {
                if (sameReadyMetadata(member, callback)) {
                    continue;
                }

                throw new RecordingCallbackConflictException(
                        "Conflicting READY callback for recordingId="
                                + member.id()
                );
            }

            if (member.status() == RecordingStatus.ERROR) {
                throw new RecordingCallbackConflictException(
                        "Conflicting READY callback for terminal ERROR recordingId="
                                + member.id()
                );
            }

            try {
                member.markReady(
                        callback.objectKey(),
                        durationMs,
                        sizeBytes,
                        readyAt,
                        callback.checksum()
                );
                member.updateRetryCount(
                        callback.retryCount()
                );
            } catch (RuntimeException ex) {
                throw new RecordingCallbackConflictException(
                        "Shared READY callback rejected for recordingId="
                                + member.id(),
                        ex
                );
            }

            Recording saved =
                    recordingRepository.save(member);

            recordingStatusCallbackPort.publish(
                    toStatusCallback(
                            saved,
                            callback.coverImageKey()
                    )
            );
        }
    }

    private void handleSharedErrorCallback(
            UUID clipGroupId,
            RecordingCallbackCommand callback
    ) {
        for (Recording member :
                recordingRepository.findByClipGroupId(
                        clipGroupId
                )) {

            if (member.status() == RecordingStatus.ERROR) {
                if (sameErrorMetadata(member, callback)) {
                    continue;
                }

                throw new RecordingCallbackConflictException(
                        "Conflicting ERROR callback for recordingId="
                                + member.id()
                );
            }

            if (member.status() == RecordingStatus.READY) {
                throw new RecordingCallbackConflictException(
                        "Conflicting ERROR callback for terminal READY recordingId="
                                + member.id()
                );
            }

            try {
                member.markError(
                        callback.errorCode()
                );
                member.updateRetryCount(
                        callback.retryCount()
                );
            } catch (RuntimeException ex) {
                throw new RecordingCallbackConflictException(
                        "Shared ERROR callback rejected for recordingId="
                                + member.id(),
                        ex
                );
            }

            Recording saved =
                    recordingRepository.save(member);

            recordingStatusCallbackPort.publish(
                    toStatusCallback(saved)
            );
        }
    }
    private void handleReadyCallback(
            Recording recording,
            RecordingCallbackCommand callback
    ) {
        if (recording.status() == RecordingStatus.READY) {
            if (sameReadyMetadata(recording, callback)) {

                if (
                        callback.coverImageKey() != null
                                && !callback.coverImageKey().isBlank()
                ) {
                    recordingStatusCallbackPort.publish(
                            toStatusCallback(
                                    recording,
                                    callback.coverImageKey()
                            )
                    );
                }

                return;
            }

            throw new RecordingCallbackConflictException(
                    "Conflicting READY callback for recordingId=" + recording.id()
            );
        }

        if (recording.status() == RecordingStatus.ERROR) {
            throw new RecordingCallbackConflictException(
                    "Conflicting READY callback for terminal ERROR recordingId=" + recording.id()
            );
        }

        try {
            recording.markReady(
                    callback.objectKey(),
                    requirePositive(callback.durationMs(), "durationMs"),
                    requirePositive(callback.sizeBytes(), "sizeBytes"),
                    Instant.now(clock),
                    callback.checksum()
            );
            recording.updateRetryCount(callback.retryCount());
        } catch (RuntimeException ex) {
            throw new RecordingCallbackConflictException(
                    "READY callback rejected for recordingId=" + recording.id(),
                    ex
            );
        }

        Recording saved =
                recordingRepository.save(
                        recording
                );

        recordingStatusCallbackPort.publish(
                toStatusCallback(
                        saved,
                        callback.coverImageKey()
                )
        );
    }

    private void handleErrorCallback(
            Recording recording,
            RecordingCallbackCommand callback
    ) {
        if (recording.status() == RecordingStatus.ERROR) {
            if (sameErrorMetadata(recording, callback)) {
                return;
            }

            throw new RecordingCallbackConflictException(
                    "Conflicting ERROR callback for recordingId=" + recording.id()
            );
        }

        if (recording.status() == RecordingStatus.READY) {
            throw new RecordingCallbackConflictException(
                    "Conflicting ERROR callback for terminal READY recordingId=" + recording.id()
            );
        }

        try {
            recording.markError(callback.errorCode());
            recording.updateRetryCount(callback.retryCount());
        } catch (RuntimeException ex) {
            throw new RecordingCallbackConflictException(
                    "ERROR callback rejected for recordingId=" + recording.id(),
                    ex
            );
        }

        Recording saved = recordingRepository.save(recording);
        recordingStatusCallbackPort.publish(toStatusCallback(saved));
    }

    private boolean sameReadyMetadata(
            Recording recording,
            RecordingCallbackCommand callback
    ) {
        return Objects.equals(recording.objectKey(), callback.objectKey())
                && Objects.equals(recording.durationMs(), callback.durationMs())
                && Objects.equals(recording.sizeBytes(), callback.sizeBytes())
                && Objects.equals(recording.checksum(), callback.checksum())
                && Objects.equals(recording.retryCount(), callback.retryCount());
    }

    private boolean sameErrorMetadata(
            Recording recording,
            RecordingCallbackCommand callback
    ) {
        return Objects.equals(recording.errorCode(), callback.errorCode())
                && Objects.equals(recording.retryCount(), callback.retryCount());
    }

    private int requirePositive(
            Integer value,
            String field
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
        return value;
    }

    private long requirePositive(
            Long value,
            String field
    ) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be > 0");
        }
        return value;
    }

    private RecordingStatusCallback toStatusCallback(
            Recording recording
    ) {
        return toStatusCallback(
                recording,
                null
        );
    }

    private RecordingStatusCallback toStatusCallback(
            Recording recording,
            String coverImageKey
    ) {
        return new RecordingStatusCallback(
                recording.id(),
                recording.violationId(),
                recording.status(),
                recording.objectKey(),
                coverImageKey,
                recording.durationMs() == null
                        ? null
                        : recording.durationMs().longValue(),
                recording.sizeBytes(),
                recording.checksum(),
                recording.errorCode()
        );
    }

    private Recording createRequestedRecording(
            StartRecordingCommand command
    ) {
        try {
            return recordingCreationService.createRequested(
                    command.violationId(),
                    command.commandId()
            );
        } catch (DataIntegrityViolationException ex) {
            return recordingRepository.findByViolationId(command.violationId())
                    .orElseThrow(() -> ex);
        }
    }

    private StartRecordingCommand withStartCommandId(
            StartRecordingCommand command,
            UUID commandId
    ) {
        if (command.commandId().equals(commandId)) {
            return command;
        }

        return new StartRecordingCommand(
                commandId,
                command.violationId(),
                command.cameraId(),
                command.sessionId(),
                command.startedAt(),
                command.preBufferSeconds(),
                command.postBufferSeconds(),
                command.maxClipSeconds()
        );
    }

    private StopRecordingCommand withStopCommandId(
            StopRecordingCommand command,
            UUID commandId
    ) {
        if (command.commandId().equals(commandId)) {
            return command;
        }

        return new StopRecordingCommand(
                commandId,
                command.violationId(),
                command.endedAt()
        );
    }
}
