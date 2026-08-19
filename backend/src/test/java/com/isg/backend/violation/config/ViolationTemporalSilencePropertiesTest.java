package com.isg.backend.violation.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ViolationTemporalSilencePropertiesTest {

    @Test
    void shouldProvideDefaultSilenceTimeout() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        assertEquals(
                Duration.ofSeconds(5),
                properties.getSilenceTimeout()
        );
    }

    @Test
    void shouldAllowPositiveSilenceTimeout() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        properties.setSilenceTimeout(
                Duration.ofSeconds(8)
        );

        assertEquals(
                Duration.ofSeconds(8),
                properties.getSilenceTimeout()
        );
    }

    @Test
    void shouldRejectZeroSilenceTimeout() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        properties.setSilenceTimeout(
                                Duration.ZERO
                        )
        );
    }

    @Test
    void shouldRejectNegativeSilenceTimeout() {
        ViolationTemporalProperties properties =
                new ViolationTemporalProperties();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        properties.setSilenceTimeout(
                                Duration.ofSeconds(-1)
                        )
        );
    }
}