package com.isg.backend.violation.service;

import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

@Component
public class ActiveViolationRegistry {

    private final ConcurrentMap<ViolationStateKey, UUID> activeViolations =
            new ConcurrentHashMap<>();

    public UUID getOrCreate(
            ViolationStateKey stateKey,
            Supplier<UUID> violationIdSupplier
    ) {
        Objects.requireNonNull(
                stateKey,
                "stateKey must not be null"
        );

        Objects.requireNonNull(
                violationIdSupplier,
                "violationIdSupplier must not be null"
        );

        return activeViolations.computeIfAbsent(
                stateKey,
                ignored -> Objects.requireNonNull(
                        violationIdSupplier.get(),
                        "violationIdSupplier must not return null"
                )
        );
    }

    public Optional<UUID> find(
            ViolationStateKey stateKey
    ) {
        Objects.requireNonNull(
                stateKey,
                "stateKey must not be null"
        );

        return Optional.ofNullable(
                activeViolations.get(
                        stateKey
                )
        );
    }

    public Optional<UUID> remove(
            ViolationStateKey stateKey
    ) {
        Objects.requireNonNull(
                stateKey,
                "stateKey must not be null"
        );

        return Optional.ofNullable(
                activeViolations.remove(
                        stateKey
                )
        );
    }

    public boolean removeIfMappedTo(
            ViolationStateKey stateKey,
            UUID violationId
    ) {
        Objects.requireNonNull(
                stateKey,
                "stateKey must not be null"
        );

        Objects.requireNonNull(
                violationId,
                "violationId must not be null"
        );

        return activeViolations.remove(
                stateKey,
                violationId
        );
    }

    public int size() {
        return activeViolations.size();
    }
}