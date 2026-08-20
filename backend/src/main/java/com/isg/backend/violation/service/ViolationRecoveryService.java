package com.isg.backend.violation.service;

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

    public ViolationRecoveryService(
            SpringDataViolationRepository violationRepository,
            ActiveViolationRegistry activeViolationRegistry,
            TemporalConfirmationService temporalConfirmationService
    ) {
        this.violationRepository = violationRepository;
        this.activeViolationRegistry = activeViolationRegistry;
        this.temporalConfirmationService = temporalConfirmationService;
    }

    @Transactional(readOnly = true)
    public int recoverInterruptedViolations() {

        List<ViolationJpaEntity> activeViolations =
                violationRepository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.ACTIVE
                        )
                );

        int recoveredCount = 0;

        for (ViolationJpaEntity violation : activeViolations) {

            UUID sourceSessionId =
                    violation.getSourceSessionId();

            String subjectKey =
                    violation.getSubjectKey();

            if (sourceSessionId == null) {
                continue;
            }

            if (subjectKey == null
                    || subjectKey.isBlank()) {
                continue;
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

            recoveredCount++;
        }

        return recoveredCount;
    }
}
