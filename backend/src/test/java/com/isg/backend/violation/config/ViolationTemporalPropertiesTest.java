package com.isg.backend.violation.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViolationTemporalPropertiesTest {

    @Test
    void shouldProvideDefaultTemporalConfiguration() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        assertEquals(
                Duration.ofMillis(1500),
                properties.getConfirmationDuration()
        );

        assertEquals(
                Duration.ofMillis(750),
                properties.getFrameGapTolerance()
        );
    }

    @Test
    void shouldAllowPositiveConfirmationDuration() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        properties.setConfirmationDuration(
                Duration.ofSeconds(2)
        );

        assertEquals(
                Duration.ofSeconds(2),
                properties.getConfirmationDuration()
        );
    }

    @Test
    void shouldRejectZeroConfirmationDuration() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        properties.setConfirmationDuration(
                                Duration.ZERO
                        )
        );
    }

    @Test
    void shouldRejectNegativeFrameGapTolerance() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        properties.setFrameGapTolerance(
                                Duration.ofMillis(-1)
                        )
        );
    }
}