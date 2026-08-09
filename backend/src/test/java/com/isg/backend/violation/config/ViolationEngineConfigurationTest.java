package com.isg.backend.violation.config;

import com.isg.backend.violation.application.port.RestrictedZonePort;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.rule.CandidateViolationEvaluator;
import com.isg.backend.violation.rule.PersonPpeMatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViolationEngineConfigurationTest {

    @Test
    void createsEvaluatorWithoutRestrictedZonePort() {
        ViolationEngineConfiguration configuration =
                new ViolationEngineConfiguration();

        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        PersonPpeMatcher matcher =
                configuration.personPpeMatcher();

        @SuppressWarnings("unchecked")
        ObjectProvider<RestrictedZonePort> provider =
                mock(ObjectProvider.class);

        when(provider.getIfAvailable())
                .thenReturn(null);

        CandidateViolationEvaluator evaluator =
                configuration.candidateViolationEvaluator(
                        matcher,
                        properties,
                        provider
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

    @Test
    void createsEvaluatorWhenRestrictedZonePortExists() {
        ViolationEngineConfiguration configuration =
                new ViolationEngineConfiguration();

        ViolationRuleProperties properties =
                new ViolationRuleProperties();

        PersonPpeMatcher matcher =
                configuration.personPpeMatcher();

        RestrictedZonePort restrictedZonePort =
                mock(RestrictedZonePort.class);

        @SuppressWarnings("unchecked")
        ObjectProvider<RestrictedZonePort> provider =
                mock(ObjectProvider.class);

        when(provider.getIfAvailable())
                .thenReturn(
                        restrictedZonePort
                );

        CandidateViolationEvaluator evaluator =
                configuration.candidateViolationEvaluator(
                        matcher,
                        properties,
                        provider
                );

        assertThat(evaluator)
                .isNotNull();
    }
}