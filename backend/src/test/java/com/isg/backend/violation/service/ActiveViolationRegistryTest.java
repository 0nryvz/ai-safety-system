package com.isg.backend.violation.service;

import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ActiveViolationRegistryTest {

    private ActiveViolationRegistry registry;

    @BeforeEach
    void setUp() {
        registry =
                new ActiveViolationRegistry();
    }

    @Test
    void createsMappingOnlyOnceForSameStateKey() {
        ViolationStateKey stateKey =
                stateKey();

        UUID firstViolationId =
                UUID.randomUUID();

        UUID secondViolationId =
                UUID.randomUUID();

        AtomicInteger supplierCalls =
                new AtomicInteger();

        UUID firstResult =
                registry.getOrCreate(
                        stateKey,
                        () -> {
                            supplierCalls.incrementAndGet();
                            return firstViolationId;
                        }
                );

        UUID secondResult =
                registry.getOrCreate(
                        stateKey,
                        () -> {
                            supplierCalls.incrementAndGet();
                            return secondViolationId;
                        }
                );

        assertThat(firstResult)
                .isEqualTo(firstViolationId);

        assertThat(secondResult)
                .isEqualTo(firstViolationId);

        assertThat(supplierCalls.get())
                .isEqualTo(1);
    }

    @Test
    void removesActiveMapping() {
        ViolationStateKey stateKey =
                stateKey();

        UUID violationId =
                UUID.randomUUID();

        registry.getOrCreate(
                stateKey,
                () -> violationId
        );

        assertThat(
                registry.remove(
                        stateKey
                )
        ).contains(
                violationId
        );

        assertThat(
                registry.find(
                        stateKey
                )
        ).isEmpty();
    }

    @Test
    void doesNotCreateMappingWhenSupplierFails() {
        ViolationStateKey stateKey =
                stateKey();

        try {
            registry.getOrCreate(
                    stateKey,
                    () -> {
                        throw new RuntimeException(
                                "persistence failure"
                        );
                    }
            );
        } catch (RuntimeException ignored) {
        }

        assertThat(
                registry.find(
                        stateKey
                )
        ).isEmpty();
    }

    private ViolationStateKey stateKey() {
        return new ViolationStateKey(
                UUID.randomUUID(),
                UUID.randomUUID(),
                ViolationType.MISSING_WELDING_MASK,
                "track-1"
        );
    }
}