package com.isg.backend.violation.service;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class DuplicateEventGuard {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final Map<UUID, Instant> processedEvents =
            new ConcurrentHashMap<>();

    private final Clock clock;
    private final Duration ttl;

    public DuplicateEventGuard() {
        this(Clock.systemUTC(), DEFAULT_TTL);
    }

    DuplicateEventGuard(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    public boolean isFirstOccurrence(UUID eventId) {
        Instant now = clock.instant();

        removeExpiredEvents(now);

        Instant previous = processedEvents.putIfAbsent(
                eventId,
                now
        );

        return previous == null;
    }

    private void removeExpiredEvents(Instant now) {
        Instant expirationThreshold = now.minus(ttl);

        processedEvents.entrySet().removeIf(
                entry -> entry.getValue()
                        .isBefore(expirationThreshold)
        );
    }
}