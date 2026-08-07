package com.isg.backend.violation.rule;

import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.detection.DetectionFrame;

import java.util.List;

public interface ViolationRule {

    List<CandidateViolation> evaluate(DetectionFrame frame);
}