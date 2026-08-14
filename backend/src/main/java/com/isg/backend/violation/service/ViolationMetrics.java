package com.isg.backend.violation.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
public class ViolationMetrics {

    static final String VALIDATION_DURATION_METRIC =
            "isg.violation.validation.duration";

    static final String DUPLICATE_COUNT_METRIC =
            "isg.violation.duplicate.count";

    static final String ACTIVE_COUNT_METRIC =
            "isg.violation.active.count";

    private final Timer validationDurationTimer;
    private final Counter duplicateCounter;

    public ViolationMetrics(
            MeterRegistry meterRegistry,
            ActiveViolationRegistry activeViolationRegistry
    ) {
        Objects.requireNonNull(
                meterRegistry,
                "meterRegistry must not be null"
        );

        Objects.requireNonNull(
                activeViolationRegistry,
                "activeViolationRegistry must not be null"
        );

        this.validationDurationTimer =
                Timer.builder(
                                VALIDATION_DURATION_METRIC
                        )
                        .description(
                                "Runtime duration of the BE3 detection validation and temporal confirmation pipeline"
                        )
                        .register(
                                meterRegistry
                        );

        this.duplicateCounter =
                Counter.builder(
                                DUPLICATE_COUNT_METRIC
                        )
                        .description(
                                "Number of duplicate detection events rejected by BE3"
                        )
                        .register(
                                meterRegistry
                        );

        Gauge.builder(
                        ACTIVE_COUNT_METRIC,
                        activeViolationRegistry,
                        ActiveViolationRegistry::size
                )
                .description(
                        "Current number of active violation mappings in BE3"
                )
                .register(
                        meterRegistry
                );
    }

    public void recordValidationDurationNanos(
            long durationNanos
    ) {
        if (durationNanos < 0) {
            return;
        }

        validationDurationTimer.record(
                durationNanos,
                TimeUnit.NANOSECONDS
        );
    }

    public void incrementDuplicateCount() {
        duplicateCounter.increment();
    }

    public <T> T recordValidation(
            Supplier<T> operation
    ) {
        Objects.requireNonNull(
                operation,
                "operation must not be null"
        );

        long startedAt =
                System.nanoTime();

        try {
            return operation.get();
        } finally {
            recordValidationDurationNanos(
                    System.nanoTime() - startedAt
            );
        }
    }
}