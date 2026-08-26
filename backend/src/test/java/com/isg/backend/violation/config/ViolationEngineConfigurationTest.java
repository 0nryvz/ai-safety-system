package com.isg.backend.violation.config;

import com.isg.backend.violation.application.port.RestrictedZonePort;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import com.isg.backend.violation.rule.PersonPpeMatcher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ViolationEngineConfigurationTest {

    @Test
    void createsEvaluatorWithRequiredRestrictedZonePort() {
        ViolationEngineConfiguration configuration =
                new ViolationEngineConfiguration();

        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        PersonPpeMatcher matcher =
                configuration.personPpeMatcher();

        RestrictedZonePort restrictedZonePort =
                mock(
                        RestrictedZonePort.class
                );

        CandidateViolationEvaluator evaluator =
                configuration.candidateViolationEvaluator(
                        matcher,
                        properties,
                        restrictedZonePort
                );

        assertThat(
                evaluator
        ).isNotNull();
    }

    @Test
    void evaluatorSafelyHandlesEmptyFrame() {
        ViolationEngineConfiguration configuration =
                new ViolationEngineConfiguration();

        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        PersonPpeMatcher matcher =
                configuration.personPpeMatcher();

        RestrictedZonePort restrictedZonePort =
                mock(
                        RestrictedZonePort.class
                );

        CandidateViolationEvaluator evaluator =
                configuration.candidateViolationEvaluator(
                        matcher,
                        properties,
                        restrictedZonePort
                );

        DetectionFrame emptyFrame =
                new DetectionFrame(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        "test-model",
                        10L,
                        List.of()
                );

        assertThat(
                evaluator.evaluate(
                        emptyFrame
                )
        ).isEmpty();
    }
}