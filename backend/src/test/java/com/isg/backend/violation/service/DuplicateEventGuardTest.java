package com.isg.backend.violation.service;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DuplicateEventGuardTest {

    private Instant currentTime =
            Instant.parse("2026-08-06T20:00:00Z");

    private final Clock adjustableClock = new Clock() {

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return currentTime;
        }
    };

    @Test
    void firstOccurrenceReturnsTrueAndDuplicateReturnsFalse() {
        DuplicateEventGuard guard =
                new DuplicateEventGuard(
                        adjustableClock,
                        Duration.ofSeconds(60)
                );

        UUID eventId = UUID.randomUUID();

        assertThat(guard.isFirstOccurrence(eventId))
                .isTrue();

        assertThat(guard.isFirstOccurrence(eventId))
                .isFalse();
    }

    @Test
    void differentEventIdsDoNotAffectEachOther() {
        DuplicateEventGuard guard =
                new DuplicateEventGuard(
                        adjustableClock,
                        Duration.ofSeconds(60)
                );

        UUID firstEventId = UUID.randomUUID();
        UUID secondEventId = UUID.randomUUID();

        assertThat(guard.isFirstOccurrence(firstEventId))
                .isTrue();

        assertThat(guard.isFirstOccurrence(secondEventId))
                .isTrue();
    }

    @Test
    void sameEventCanBeProcessedAgainAfterTtlExpires() {
        DuplicateEventGuard guard =
                new DuplicateEventGuard(
                        adjustableClock,
                        Duration.ofSeconds(60)
                );

        UUID eventId = UUID.randomUUID();

        assertThat(guard.isFirstOccurrence(eventId))
                .isTrue();

        currentTime = currentTime.plusSeconds(61);

        assertThat(guard.isFirstOccurrence(eventId))
                .isTrue();
    }
}