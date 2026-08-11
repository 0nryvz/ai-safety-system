package com.isg.backend.violation.query;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViolationQueryFilterTest {

    @Test
    void acceptsValidDateRange() {
        Instant from =
                Instant.parse(
                        "2026-08-11T10:00:00Z"
                );

        Instant to =
                Instant.parse(
                        "2026-08-11T11:00:00Z"
                );

        assertThatCode(
                () -> new ViolationQueryFilter(
                        from,
                        to,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void acceptsOpenEndedDateRange() {
        assertThatCode(
                () -> new ViolationQueryFilter(
                        Instant.now(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        ).doesNotThrowAnyException();
    }

    @Test
    void rejectsReversedDateRange() {
        Instant from =
                Instant.parse(
                        "2026-08-11T12:00:00Z"
                );

        Instant to =
                Instant.parse(
                        "2026-08-11T11:00:00Z"
                );

        assertThatThrownBy(
                () -> new ViolationQueryFilter(
                        from,
                        to,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessageContaining(
                        "from"
                );
    }
}