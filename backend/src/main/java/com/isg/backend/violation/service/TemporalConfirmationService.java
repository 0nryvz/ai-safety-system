package com.isg.backend.violation.service;

import com.isg.backend.violation.config.ViolationTemporalProperties;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.temporal.CandidateViolationState;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
import com.isg.backend.violation.domain.temporal.EndedViolation;
import com.isg.backend.violation.domain.temporal.TemporalViolationTransitions;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TemporalConfirmationService {

    private final ViolationTemporalProperties properties;

    private final Map<ViolationStateKey, CandidateViolationState> states =
            new ConcurrentHashMap<>();

    private final Map<CooldownKey, Instant> cooldownUntil =
            new HashMap<>();

    public TemporalConfirmationService(
            ViolationTemporalProperties properties
    ) {
        this.properties = properties;
    }

    public synchronized List<ConfirmedViolation> processFrame(
            Instant frameTimestamp,
            List<CandidateViolation> candidates
    ) {
        return processFrameTransitions(
                frameTimestamp,
                candidates
        ).started();
    }

    public synchronized TemporalViolationTransitions processFrameTransitions(
            Instant frameTimestamp,
            List<CandidateViolation> candidates
    ) {
        List<CandidateViolation> safeCandidates =
                candidates == null
                        ? List.of()
                        : List.copyOf(candidates);

        clearExpiredCooldowns(
                frameTimestamp
        );

        Set<ViolationStateKey> observedKeys =
                new HashSet<>();

        List<ConfirmedViolation> confirmations =
                new ArrayList<>();

        for (CandidateViolation candidate : safeCandidates) {
            ViolationStateKey key =
                    ViolationStateKey.from(candidate);

            observedKeys.add(key);

            CandidateViolationState state =
                    states.get(key);

            if (state == null) {
                states.put(
                        key,
                        new CandidateViolationState(candidate)
                );

                continue;
            }

            Duration gap =
                    Duration.between(
                            state.lastSeenAt(),
                            candidate.frameTimestamp()
                    );

            if (gap.compareTo(
                    properties.getFrameGapTolerance()
            ) > 0) {
                if (!state.confirmed()) {
                    states.put(
                            key,
                            new CandidateViolationState(candidate)
                    );
                }

                continue;
            }

            state.observe(candidate);

            if (state.confirmed()) {
                continue;
            }

            Duration candidateDuration =
                    Duration.between(
                            state.candidateStartedAt(),
                            state.lastSeenAt()
                    );

            if (candidateDuration.compareTo(
                    properties.getConfirmationDuration()
            ) >= 0) {
                if (isCooldownActive(
                        candidate.cameraId(),
                        candidate.violationType(),
                        candidate.frameTimestamp()
                )) {
                    continue;
                }

                state.markConfirmed();

                confirmations.add(
                        new ConfirmedViolation(
                                key,
                                candidate.cameraId(),
                                candidate.sessionId(),
                                candidate.violationType(),
                                state.candidateStartedAt(),
                                candidate.frameTimestamp(),
                                state.averageConfidence()
                        )
                );
            }
        }

        List<EndedViolation> endedViolations =
                clearExpiredStatesAndCollectEnds(
                        frameTimestamp,
                        observedKeys
                );

        return new TemporalViolationTransitions(
                confirmations,
                endedViolations
        );
    }

    private List<EndedViolation> clearExpiredStatesAndCollectEnds(
            Instant frameTimestamp,
            Set<ViolationStateKey> observedKeys
    ) {
        List<EndedViolation> endedViolations =
                new ArrayList<>();

        states.entrySet().removeIf(entry -> {
            if (observedKeys.contains(entry.getKey())) {
                return false;
            }

            ViolationStateKey key =
                    entry.getKey();

            CandidateViolationState state =
                    entry.getValue();

            Duration absence =
                    Duration.between(
                            state.lastSeenAt(),
                            frameTimestamp
                    );

            if (absence.compareTo(
                    properties.getFrameGapTolerance()
            ) <= 0) {
                return false;
            }

            if (state.confirmed()) {
                endedViolations.add(
                        new EndedViolation(
                                key,
                                key.cameraId(),
                                key.sessionId(),
                                key.violationType(),
                                state.lastSeenAt()
                        )
                );

                startCooldown(
                        key.cameraId(),
                        key.violationType(),
                        frameTimestamp
                );
            }

            return true;
        });

        return List.copyOf(
                endedViolations
        );
    }

    private boolean isCooldownActive(
            UUID cameraId,
            ViolationType violationType,
            Instant timestamp
    ) {
        Instant until =
                cooldownUntil.get(
                        new CooldownKey(
                                cameraId,
                                violationType
                        )
                );

        return until != null
                && timestamp.isBefore(until);
    }

    private void startCooldown(
            UUID cameraId,
            ViolationType violationType,
            Instant startedAt
    ) {
        cooldownUntil.put(
                new CooldownKey(
                        cameraId,
                        violationType
                ),
                startedAt.plus(
                        properties.getCooldownDuration()
                )
        );
    }

    private void clearExpiredCooldowns(
            Instant timestamp
    ) {
        cooldownUntil.entrySet()
                .removeIf(
                        entry ->
                                !timestamp.isBefore(
                                        entry.getValue()
                                )
                );
    }

    private record CooldownKey(
            UUID cameraId,
            ViolationType violationType
    ) {
    }
}