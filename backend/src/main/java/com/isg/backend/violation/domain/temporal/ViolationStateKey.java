package com.isg.backend.violation.domain.temporal;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.ViolationType;

import java.util.Objects;
import java.util.UUID;

/**
 * Identifies a temporal violation state for a camera session.
 *
 * <p>When the AI detection contains a stable person track identifier,
 * the track-based person key is used as the subject key. This allows
 * different tracked people in the same camera session to maintain
 * independent temporal states.</p>
 *
 * <p>MVP limitation: when a stable track id is not available, the
 * frame-local person key produced by the detection pipeline cannot be
 * used across consecutive frames because it changes with each event.
 * In that case the temporal engine falls back to a shared
 * {@code "untracked"} subject key.</p>
 *
 * <p>Therefore, without track ids, candidates with the same camera,
 * session and violation type are treated as a single active temporal
 * subject. Multiple untracked people producing the same violation type
 * simultaneously in the same camera session cannot be distinguished
 * by the temporal confirmation engine.</p>
 */
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