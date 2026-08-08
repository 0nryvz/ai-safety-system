package com.isg.backend.violation.domain.temporal;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;

import java.util.Objects;
import java.util.UUID;

public record ViolationStateKey(
        UUID cameraId,
        UUID sessionId,
        ViolationType violationType,
        String subjectKey
) {

    private static final String TRACK_PREFIX = "track-";
    private static final String UNTRACKED_SUBJECT = "untracked";

    public ViolationStateKey {
        Objects.requireNonNull(
                cameraId,
                "cameraId must not be null"
        );

        Objects.requireNonNull(
                sessionId,
                "sessionId must not be null"
        );

        Objects.requireNonNull(
                violationType,
                "violationType must not be null"
        );

        if (subjectKey == null || subjectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "subjectKey must not be blank"
            );
        }
    }

    public static ViolationStateKey from(
            CandidateViolation candidate
    ) {
        Objects.requireNonNull(
                candidate,
                "candidate must not be null"
        );

        return new ViolationStateKey(
                candidate.cameraId(),
                candidate.sessionId(),
                candidate.violationType(),
                resolveSubjectKey(candidate.personKey())
        );
    }

    private static String resolveSubjectKey(
            String personKey
    ) {
        if (personKey != null
                && personKey.startsWith(TRACK_PREFIX)) {
            return personKey;
        }

        return UNTRACKED_SUBJECT;
    }
}