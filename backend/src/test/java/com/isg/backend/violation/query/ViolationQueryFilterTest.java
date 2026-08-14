package com.isg.backend.violation.query;

import com.isg.backend.violation.exception.InvalidViolationQueryException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViolationQueryFilterTest {

    @Test
    void acceptsValidDateRange() {
        Instant from =
                Instant.parse(
                        "2026-08-10T10:00:00Z"
                );

        Instant to =
                Instant.parse(
                        "2026-08-10T12:00:00Z"
                );

        ViolationQueryFilter filter =
                new ViolationQueryFilter(
                        from,
                        to,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertThat(filter.from())
                .isEqualTo(
                        from
                );

        assertThat(filter.to())
                .isEqualTo(
                        to
                );
    }

    @Test
    void acceptsOpenEndedDateRange() {
        Instant from =
                Instant.parse(
                        "2026-08-10T10:00:00Z"
                );

        ViolationQueryFilter filter =
                new ViolationQueryFilter(
                        from,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                );

        assertThat(filter.from())
                .isEqualTo(
                        from
                );

        assertThat(filter.to())
                .isNull();
    }

    @Test
    void rejectsReversedDateRange() {
        Instant from =
                Instant.parse(
                        "2026-08-10T12:00:00Z"
                );

        Instant to =
                Instant.parse(
                        "2026-08-10T10:00:00Z"
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
                        InvalidViolationQueryException.class
                )
                .hasMessage(
                        "from must not be after to"
                );
    }
}