package com.isg.backend.violation.rule;

import com.isg.backend.violation.dto.DetectionRequest;
import com.isg.backend.violation.domain.CandidateViolation;

import java.util.List;

public interface ViolationRule {

    List<CandidateViolation> evaluate(DetectionRequest request);

}