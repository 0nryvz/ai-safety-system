package com.isg.backend.violation.service;

import com.isg.backend.violation.config.ViolationTemporalProperties;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.temporal.CandidateViolationState;
import com.isg.backend.violation.domain.temporal.ConfirmedViolation;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TemporalConfirmationService {

    private final ViolationTemporalProperties properties;

    private final Map<ViolationStateKey, CandidateViolationState> states =
            new ConcurrentHashMap<>();

    public TemporalConfirmationService(
            ViolationTemporalProperties properties
    ) {
        this.properties = properties;
    }

    public synchronized List<ConfirmedViolation> processFrame(
            Instant frameTimestamp,
            List<CandidateViolation> candidates
    ) {
        List<CandidateViolation> safeCandidates =
                candidates == null
                        ? List.of()
                        : List.copyOf(candidates);

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
                states.put(
                        key,
                        new CandidateViolationState(candidate)
                );

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

        clearExpiredUnconfirmedStates(
                frameTimestamp,
                observedKeys
        );

        return List.copyOf(confirmations);
    }

    private void clearExpiredUnconfirmedStates(
            Instant frameTimestamp,
            Set<ViolationStateKey> observedKeys
    ) {
        states.entrySet().removeIf(entry -> {
            if (observedKeys.contains(entry.getKey())) {
                return false;
            }

            CandidateViolationState state =
                    entry.getValue();

            if (state.confirmed()) {
                return false;
            }

            Duration absence =
                    Duration.between(
                            state.lastSeenAt(),
                            frameTimestamp
                    );

            return absence.compareTo(
                    properties.getFrameGapTolerance()
            ) > 0;
        });
    }
}