package com.isg.backend.violation.service;

import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ViolationMetricsTest {

    private SimpleMeterRegistry meterRegistry;
    private ActiveViolationRegistry activeViolationRegistry;
    private ViolationMetrics violationMetrics;

    @BeforeEach
    void setUp() {
        meterRegistry =
                new SimpleMeterRegistry();

        activeViolationRegistry =
                new ActiveViolationRegistry();

        violationMetrics =
                new ViolationMetrics(
                        meterRegistry,
                        activeViolationRegistry
                );
    }

    @Test
    void recordsValidationDuration() {
        violationMetrics.recordValidationDurationNanos(
                TimeUnit.MILLISECONDS.toNanos(
                        25
                )
        );

        Timer timer =
                meterRegistry.find(
                                ViolationMetrics.VALIDATION_DURATION_METRIC
                        )
                        .timer();

        assertThat(timer)
                .isNotNull();

        assertThat(
                timer.count()
        ).isEqualTo(
                1
        );

        assertThat(
                timer.totalTime(
                        TimeUnit.MILLISECONDS
                )
        ).isGreaterThanOrEqualTo(
                25.0
        );
    }

    @Test
    void incrementsDuplicateCounter() {
        violationMetrics.incrementDuplicateCount();

        violationMetrics.incrementDuplicateCount();

        Counter counter =
                meterRegistry.find(
                                ViolationMetrics.DUPLICATE_COUNT_METRIC
                        )
                        .counter();

        assertThat(counter)
                .isNotNull();

        assertThat(
                counter.count()
        ).isEqualTo(
                2.0
        );
    }

    @Test
    void activeViolationGaugeReflectsRegistrySize() {
        Gauge gauge =
                meterRegistry.find(
                                ViolationMetrics.ACTIVE_COUNT_METRIC
                        )
                        .gauge();

        assertThat(gauge)
                .isNotNull();

        assertThat(
                gauge.value()
        ).isZero();

        ViolationStateKey firstStateKey =
                new ViolationStateKey(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ViolationType.MISSING_GLOVES,
                        "track-1"
                );

        ViolationStateKey secondStateKey =
                new ViolationStateKey(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        ViolationType.MISSING_WELDING_MASK,
                        "track-2"
                );

        activeViolationRegistry.getOrCreate(
                firstStateKey,
                UUID::randomUUID
        );

        activeViolationRegistry.getOrCreate(
                secondStateKey,
                UUID::randomUUID
        );

        assertThat(
                gauge.value()
        ).isEqualTo(
                2.0
        );

        activeViolationRegistry.remove(
                firstStateKey
        );

        assertThat(
                gauge.value()
        ).isEqualTo(
                1.0
        );
    }

    @Test
    void negativeValidationDurationIsIgnored() {
        violationMetrics.recordValidationDurationNanos(
                -1L
        );

        Timer timer =
                meterRegistry.find(
                                ViolationMetrics.VALIDATION_DURATION_METRIC
                        )
                        .timer();

        assertThat(timer)
                .isNotNull();

        assertThat(
                timer.count()
        ).isZero();
    }
}