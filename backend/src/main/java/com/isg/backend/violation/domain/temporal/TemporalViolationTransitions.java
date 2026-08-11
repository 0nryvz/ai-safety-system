package com.isg.backend.violation.domain.temporal;

import java.util.List;

public record TemporalViolationTransitions(
        List<ConfirmedViolation> started,
        List<EndedViolation> ended
) {

    public TemporalViolationTransitions {
        started = started == null
                ? List.of()
                : List.copyOf(started);

        ended = ended == null
                ? List.of()
                : List.copyOf(ended);
    }
}