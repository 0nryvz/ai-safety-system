package com.isg.backend.violation.config;

import com.isg.backend.violation.application.port.RestrictedZonePort;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import com.isg.backend.violation.rule.MissingGlovesRule;
import com.isg.backend.violation.rule.MissingWeldingApronRule;
import com.isg.backend.violation.rule.MissingWeldingMaskRule;
import com.isg.backend.violation.rule.PersonPpeMatcher;
import com.isg.backend.violation.rule.RestrictedZoneRule;
import com.isg.backend.violation.rule.UnprotectedPersonRule;
import com.isg.backend.violation.rule.ViolationRule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
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
            ObjectProvider<RestrictedZonePort> restrictedZonePortProvider
    ) {
        List<ViolationRule> rules =
                new ArrayList<>();

        rules.add(
                new MissingWeldingMaskRule()
        );

        rules.add(
                new MissingGlovesRule()
        );

        rules.add(
                new MissingWeldingApronRule()
        );

        rules.add(
                new UnprotectedPersonRule(
                        properties
                )
        );

        RestrictedZonePort restrictedZonePort =
                restrictedZonePortProvider.getIfAvailable();

        if (restrictedZonePort != null) {
            rules.add(
                    new RestrictedZoneRule(
                            restrictedZonePort
                    )
            );
        }

        return new CandidateViolationEvaluator(
                rules,
                personPpeMatcher,
                properties
        );
    }
}