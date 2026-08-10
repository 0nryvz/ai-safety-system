package com.isg.backend.violation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "violation.temporal")
public class ViolationTemporalProperties {

    private Duration confirmationDuration =
            Duration.ofMillis(1500);

    private Duration frameGapTolerance =
            Duration.ofMillis(750);

    public Duration getConfirmationDuration() {
        return confirmationDuration;
    }

    public void setConfirmationDuration(
            Duration confirmationDuration
    ) {
        validatePositive(
                confirmationDuration,
                "confirmationDuration"
        );

        this.confirmationDuration =
                confirmationDuration;
    }

    public Duration getFrameGapTolerance() {
        return frameGapTolerance;
    }

    public void setFrameGapTolerance(
            Duration frameGapTolerance
    ) {
        validatePositive(
                frameGapTolerance,
                "frameGapTolerance"
        );

        this.frameGapTolerance =
                frameGapTolerance;
    }

    private static void validatePositive(
            Duration duration,
            String fieldName
    ) {
        if (duration == null
                || duration.isZero()
                || duration.isNegative()) {
            throw new IllegalArgumentException(
                    fieldName + " must be positive"
            );
        }
    }
}