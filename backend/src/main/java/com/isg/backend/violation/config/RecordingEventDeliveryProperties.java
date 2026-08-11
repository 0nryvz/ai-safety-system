package com.isg.backend.violation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "violation.recording.delivery")
public class RecordingEventDeliveryProperties {

    /*
     * MVP retry policy.
     *
     * The value is configurable so that the retry policy is not
     * hard-wired into the event delivery implementation.
     *
     * Three total attempts are used as the current MVP default.
     * Durable retry/outbox is intentionally left for the later
     * reconciliation/outbox stage.
     */
    private int maxAttempts = 3;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(
            int maxAttempts
    ) {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException(
                    "maxAttempts must be positive"
            );
        }

        this.maxAttempts =
                maxAttempts;
    }
}