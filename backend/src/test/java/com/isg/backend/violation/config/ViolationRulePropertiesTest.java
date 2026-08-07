package com.isg.backend.violation.config;

import com.isg.backend.violation.domain.ViolationType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViolationRulePropertiesTest {

    @Test
    void usesDefaultContainmentThreshold() {
        ViolationRuleProperties properties = new ViolationRuleProperties();

        assertThat(properties.getContainmentThreshold())
                .isEqualTo(0.50);
    }

    @Test
    void usesDefaultConfidenceThresholds() {
        ViolationRuleProperties properties = new ViolationRuleProperties();

        assertThat(
                properties.confidenceThresholdFor(
                        ViolationType.MISSING_WELDING_MASK
                )
        ).isEqualTo(0.60);

        assertThat(
                properties.confidenceThresholdFor(
                        ViolationType.MISSING_GLOVES
                )
        ).isEqualTo(0.60);

        assertThat(
                properties.confidenceThresholdFor(
                        ViolationType.MISSING_WELDING_APRON
                )
        ).isEqualTo(0.60);

        assertThat(
                properties.confidenceThresholdFor(
                        ViolationType.RESTRICTED_ZONE
                )
        ).isEqualTo(0.60);

        assertThat(
                properties.confidenceThresholdFor(
                        ViolationType.UNPROTECTED_PERSON
                )
        ).isEqualTo(0.60);
    }

    @Test
    void allowsContainmentThresholdOverride() {
        ViolationRuleProperties properties = new ViolationRuleProperties();

        properties.setContainmentThreshold(0.75);

        assertThat(properties.getContainmentThreshold())
                .isEqualTo(0.75);
    }

    @Test
    void allowsConfidenceThresholdOverride() {
        ViolationRuleProperties properties = new ViolationRuleProperties();

        properties.setConfidenceThresholds(Map.of(
                ViolationType.MISSING_GLOVES,
                0.80
        ));

        assertThat(
                properties.confidenceThresholdFor(
                        ViolationType.MISSING_GLOVES
                )
        ).isEqualTo(0.80);
    }

    @Test
    void rejectsContainmentThresholdOutsideNormalizedRange() {
        ViolationRuleProperties properties = new ViolationRuleProperties();

        assertThatThrownBy(() ->
                properties.setContainmentThreshold(1.10))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsConfidenceThresholdOutsideNormalizedRange() {
        ViolationRuleProperties properties = new ViolationRuleProperties();

        assertThatThrownBy(() ->
                properties.setConfidenceThresholds(Map.of(
                        ViolationType.MISSING_WELDING_MASK,
                        -0.10
                )))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void throwsWhenConfidenceThresholdIsMissing() {
        ViolationRuleProperties properties = new ViolationRuleProperties();

        properties.setConfidenceThresholds(Map.of(
                ViolationType.MISSING_GLOVES,
                0.70
        ));

        assertThatThrownBy(() ->
                properties.confidenceThresholdFor(
                        ViolationType.RESTRICTED_ZONE
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No confidence threshold configured");
    }
}