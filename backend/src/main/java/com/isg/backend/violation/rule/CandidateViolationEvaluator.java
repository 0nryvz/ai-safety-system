package com.isg.backend.violation.rule;

import com.isg.backend.violation.config.ViolationRuleProperties;
import com.isg.backend.violation.domain.CandidateViolation;
import com.isg.backend.violation.domain.PersonContext;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.detection.DetectionFrame;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CandidateViolationEvaluator {

    private final List<ViolationRule> rules;
    private final PersonPpeMatcher personPpeMatcher;
    private final ViolationRuleProperties properties;

    public CandidateViolationEvaluator(
            List<ViolationRule> rules,
            PersonPpeMatcher personPpeMatcher,
            ViolationRuleProperties properties
    ) {
        this.rules = List.copyOf(rules);
        this.personPpeMatcher = personPpeMatcher;
        this.properties = properties;
    }

    public List<CandidateViolation> evaluate(
            DetectionFrame frame
    ) {
        List<PersonContext> persons =
                personPpeMatcher.buildPersonContexts(
                        frame,
                        properties.getContainmentThreshold()
                );

        Map<CandidateKey, CandidateViolation> uniqueCandidates =
                new LinkedHashMap<>();

        for (PersonContext person : persons) {
            for (ViolationRule rule : rules) {
                if (!passesConfidenceThreshold(person, rule)) {
                    continue;
                }

                rule.evaluate(person, frame)
                        .ifPresent(candidate ->
                                uniqueCandidates.putIfAbsent(
                                        new CandidateKey(
                                                candidate.personKey(),
                                                candidate.violationType()
                                        ),
                                        candidate
                                )
                        );
            }
        }

        return List.copyOf(
                new ArrayList<>(uniqueCandidates.values())
        );
    }

    private boolean passesConfidenceThreshold(
            PersonContext person,
            ViolationRule rule
    ) {
        double threshold =
                properties.confidenceThresholdFor(
                        rule.supportedType()
                );

        return person.person().confidence() >= threshold;
    }

    private record CandidateKey(
            String personKey,
            ViolationType violationType
    ) {
    }
}