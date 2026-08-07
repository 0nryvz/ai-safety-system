package com.isg.backend.violation.config;

import com.isg.backend.violation.domain.ViolationType;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "violation.rules")
public class ViolationRuleProperties {

    private double containmentThreshold = 0.50;

    private Map<ViolationType, Double> confidenceThresholds =
            defaultConfidenceThresholds();

    public double getContainmentThreshold() {
        return containmentThreshold;
    }

    public void setContainmentThreshold(double containmentThreshold) {
        validateThreshold(containmentThreshold, "containmentThreshold");
        this.containmentThreshold = containmentThreshold;
    }

    public Map<ViolationType, Double> getConfidenceThresholds() {
        return confidenceThresholds;
    }

    public void setConfidenceThresholds(
            Map<ViolationType, Double> confidenceThresholds
    ) {
        if (confidenceThresholds == null) {
            this.confidenceThresholds = defaultConfidenceThresholds();
            return;
        }

        EnumMap<ViolationType, Double> validated =
                new EnumMap<>(ViolationType.class);

        confidenceThresholds.forEach((type, threshold) -> {
            validateThreshold(
                    threshold,
                    "confidenceThresholds." + type
            );
            validated.put(type, threshold);
        });

        this.confidenceThresholds = validated;
    }

    public double confidenceThresholdFor(ViolationType type) {
        Double threshold = confidenceThresholds.get(type);

        if (threshold == null) {
            throw new IllegalArgumentException(
                    "No confidence threshold configured for " + type
            );
        }

        return threshold;
    }

    private static Map<ViolationType, Double> defaultConfidenceThresholds() {
        EnumMap<ViolationType, Double> defaults =
                new EnumMap<>(ViolationType.class);

        defaults.put(ViolationType.MISSING_WELDING_MASK, 0.60);
        defaults.put(ViolationType.MISSING_GLOVES, 0.60);
        defaults.put(ViolationType.MISSING_WELDING_APRON, 0.60);
        defaults.put(ViolationType.RESTRICTED_ZONE, 0.60);
        defaults.put(ViolationType.UNPROTECTED_PERSON, 0.60);

        return defaults;
    }

    private static void validateThreshold(
            double threshold,
            String fieldName
    ) {
        if (threshold < 0.0 || threshold > 1.0) {
            throw new IllegalArgumentException(
                    fieldName + " must be between 0.0 and 1.0"
            );
        }
    }
}