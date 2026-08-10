package com.isg.backend.violation.service;

import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ActiveViolationRegistry {

    private final ConcurrentMap<ViolationStateKey, UUID> activeViolations =
            new ConcurrentHashMap<>();

    public void register(
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

        activeViolations.putIfAbsent(
                stateKey,
                violationId
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
}