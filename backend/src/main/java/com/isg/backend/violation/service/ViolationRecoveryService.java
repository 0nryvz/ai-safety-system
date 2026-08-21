package com.isg.backend.violation.service;

import com.isg.backend.violation.application.port.RecordingQueryPort;
import com.isg.backend.violation.application.port.RecordingQueryResult;
import com.isg.backend.violation.application.port.RecordingStatusCallbackPort;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ViolationRecoveryService {

    private final SpringDataViolationRepository violationRepository;
    private final ActiveViolationRegistry activeViolationRegistry;
    private final TemporalConfirmationService temporalConfirmationService;
    private final RecordingQueryPort recordingQueryPort;
    private final RecordingStatusCallbackPort recordingStatusCallbackPort;

    public ViolationRecoveryService(
            SpringDataViolationRepository violationRepository,
            ActiveViolationRegistry activeViolationRegistry,
            TemporalConfirmationService temporalConfirmationService,
            RecordingQueryPort recordingQueryPort,
            RecordingStatusCallbackPort recordingStatusCallbackPort
    ) {
        this.violationRepository =
                violationRepository;

        this.activeViolationRegistry =
                activeViolationRegistry;

        this.temporalConfirmationService =
                temporalConfirmationService;

        this.recordingQueryPort =
                recordingQueryPort;

        this.recordingStatusCallbackPort =
                recordingStatusCallbackPort;
    }

    @Transactional
    public int recoverInterruptedViolations() {

        List<ViolationJpaEntity> activeViolations =
                violationRepository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.ACTIVE
                        )
                );

        List<ViolationJpaEntity> preparingViolations =
                violationRepository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.PREPARING
                        )
                );

        int recoveredCount = 0;

        for (ViolationJpaEntity violation : activeViolations) {

            restoreActiveViolation(
                    violation
            );

            recoveredCount++;
        }

        for (ViolationJpaEntity violation : preparingViolations) {

            reconcilePreparingViolation(
                    violation
            );

            recoveredCount++;
        }

        return recoveredCount;
    }

    private void restoreActiveViolation(
            ViolationJpaEntity violation
    ) {
        UUID sourceSessionId =
                violation.getSourceSessionId();

        String subjectKey =
                violation.getSubjectKey();

        if (sourceSessionId == null) {
            return;
        }

        if (subjectKey == null
                || subjectKey.isBlank()) {
            return;
        }

        ViolationStateKey stateKey =
                new ViolationStateKey(
                        violation.getCameraId(),
                        sourceSessionId,
                        violation.getViolationType(),
                        subjectKey
                );

        activeViolationRegistry.restore(
                stateKey,
                violation.getId()
        );

        Instant startedAt =
                violation.getStartedAt();

        temporalConfirmationService.restoreConfirmedState(
                stateKey,
                startedAt,
                startedAt,
                violation.getConfidence().doubleValue()
        );
    }

    private void reconcilePreparingViolation(
            ViolationJpaEntity violation
    ) {
        RecordingQueryResult recording =
                recordingQueryPort.findByViolationId(
                        violation.getId()
                ).orElse(null);

        if (recording == null) {
            return;
        }

        if ("READY".equals(
                recording.recordingStatus()
        )) {
            recordingStatusCallbackPort.recordingReady(
                    violation.getId(),
                    Instant.now()
            );
        }

        if ("ERROR".equals(
                recording.recordingStatus()
        )) {
            recordingStatusCallbackPort.recordingError(
                    violation.getId(),
                    Instant.now(),
                    "RECOVERY_ERROR"
            );
        }
    }
}