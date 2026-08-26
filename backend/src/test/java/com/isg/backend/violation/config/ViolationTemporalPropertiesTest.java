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

        assertEquals(
                Duration.ofSeconds(10),
                properties.getCooldownDuration()
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
    void shouldAllowPositiveFrameGapTolerance() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        properties.setFrameGapTolerance(
                Duration.ofSeconds(1)
        );

        assertEquals(
                Duration.ofSeconds(1),
                properties.getFrameGapTolerance()
        );
    }

    @Test
    void shouldAllowPositiveCooldownDuration() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        properties.setCooldownDuration(
                Duration.ofSeconds(5)
        );

        assertEquals(
                Duration.ofSeconds(5),
                properties.getCooldownDuration()
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

    @Test
    void shouldRejectZeroCooldownDuration() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        properties.setCooldownDuration(
                                Duration.ZERO
                        )
        );
    }

    @Test
    void shouldRejectNegativeCooldownDuration() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        properties.setCooldownDuration(
                                Duration.ofSeconds(-1)
                        )
        );
    }
}