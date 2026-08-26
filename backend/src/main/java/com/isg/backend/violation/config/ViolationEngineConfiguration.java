package com.isg.backend.violation.config;

import com.isg.backend.violation.application.port.RestrictedZonePort;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import com.isg.backend.violation.rule.MissingGlovesRule;
import com.isg.backend.violation.rule.MissingWeldingMaskRule;
import com.isg.backend.violation.rule.PersonPpeMatcher;
import com.isg.backend.violation.rule.RestrictedZoneRule;
import com.isg.backend.violation.rule.UnprotectedPersonRule;
import com.isg.backend.violation.rule.ViolationRule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ViolationEngineConfiguration {

    @Bean
    public PersonPpeMatcher personPpeMatcher() {
        return new PersonPpeMatcher();
    }

    @Bean
    public CandidateViolationEvaluator candidateViolationEvaluator(
            PersonPpeMatcher personPpeMatcher,
            ViolationRuleProperties properties,
            RestrictedZonePort restrictedZonePort
    ) {
        List<ViolationRule> rules =
                List.of(
                        new MissingWeldingMaskRule(),
                        new MissingGlovesRule(),
                        new UnprotectedPersonRule(
                                properties
                        ),
                        new RestrictedZoneRule(
                                restrictedZonePort
                        )
                );

        return new CandidateViolationEvaluator(
                rules,
                personPpeMatcher,
                properties
        );
    }
}