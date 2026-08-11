package com.isg.backend.violation.rule;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.DetectionFrame;

import java.util.Optional;

public interface ViolationRule {

    ViolationType supportedType();

    Optional<CandidateViolation> evaluate(
            PersonContext person,
            DetectionFrame frame
    );
}
