package com.isg.backend.violation.domain.temporal;

import com.isg.backend.violation.domain.CandidateViolation;

import java.time.Instant;
import java.util.Objects;

public class CandidateViolationState {

    private final Instant candidateStartedAt;

    private Instant lastSeenAt;

    private double confidenceSum;

    private long observationCount;

    private boolean confirmed;

    public CandidateViolationState(
            CandidateViolation candidate
    ) {
        Objects.requireNonNull(
                candidate,
                "candidate must not be null"
        );

        this.candidateStartedAt =
                candidate.frameTimestamp();

        this.lastSeenAt =
                candidate.frameTimestamp();

        this.confidenceSum =
                candidate.confidence();

        this.observationCount = 1L;
        this.confirmed = false;
    }

    public static CandidateViolationState recoveredConfirmed(
            Instant candidateStartedAt,
            Instant lastSeenAt,
            double confidence
    ) {
        Objects.requireNonNull(
                candidateStartedAt,
                "candidateStartedAt must not be null"
        );

        Objects.requireNonNull(
                lastSeenAt,
                "lastSeenAt must not be null"
        );

        if (lastSeenAt.isBefore(candidateStartedAt)) {
            throw new IllegalArgumentException(
                    "lastSeenAt must not be before candidateStartedAt"
            );
        }

        CandidateViolationState state =
                new CandidateViolationState(
                        candidateStartedAt,
                        lastSeenAt,
                        confidence
                );

        state.markConfirmed();

        return state;
    }

    private CandidateViolationState(
            Instant candidateStartedAt,
            Instant lastSeenAt,
            double confidence
    ) {
        this.candidateStartedAt =
                candidateStartedAt;

        this.lastSeenAt =
                lastSeenAt;

        this.confidenceSum =
                confidence;

        this.observationCount = 1L;
        this.confirmed = false;
    }

    public void observe(
            CandidateViolation candidate
    ) {
        Objects.requireNonNull(
                candidate,
                "candidate must not be null"
        );

        if (candidate.frameTimestamp()
                .isBefore(lastSeenAt)) {
            return;
        }

        lastSeenAt =
                candidate.frameTimestamp();

        confidenceSum +=
                candidate.confidence();

        observationCount++;
    }

    public Instant candidateStartedAt() {
        return candidateStartedAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    public double averageConfidence() {
        return confidenceSum / observationCount;
    }

    public long observationCount() {
        return observationCount;
    }

    public boolean confirmed() {
        return confirmed;
    }

    public void markConfirmed() {
        this.confirmed = true;
    }

    public void markUnconfirmed() {
        this.confirmed = false;
    }
}