package com.isg.backend.violation.service;

import com.isg.backend.violation.config.ViolationTemporalProperties;
import com.isg.backend.violation.domain.ViolationLifecycleStatus;
import com.isg.backend.violation.domain.ViolationReviewStatus;
import com.isg.backend.violation.domain.ViolationType;
import com.isg.backend.violation.domain.temporal.ViolationStateKey;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
import com.isg.backend.violation.infrastructure.persistence.ViolationJpaEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ViolationRecoveryServiceTest {

    private SpringDataViolationRepository repository;

    private ActiveViolationRegistry registry;

    private TemporalConfirmationService temporalService;

    private ViolationRecoveryService service;


    @BeforeEach
    void setUp() {

        repository =
                mock(
                        SpringDataViolationRepository.class
                );

        registry =
                new ActiveViolationRegistry();

        temporalService =
                new TemporalConfirmationService(
                        new ViolationTemporalProperties()
                );

        service =
                new ViolationRecoveryService(
                        repository,
                        registry,
                        temporalService
                );
    }


    @Test
    void restoresActiveViolationState() {

        UUID violationId =
                UUID.randomUUID();

        UUID cameraId =
                UUID.randomUUID();

        UUID sessionId =
                UUID.randomUUID();

        Instant startedAt =
                Instant.parse(
                        "2026-08-19T10:00:00Z"
                );

        ViolationJpaEntity violation =
                new ViolationJpaEntity(
                        violationId,
                        cameraId,
                        UUID.randomUUID(),
                        sessionId,
                        UUID.randomUUID(),
                        ViolationType.MISSING_GLOVES,
                        startedAt,
                        BigDecimal.valueOf(0.90),
                        "model-v1",
                        ViolationLifecycleStatus.ACTIVE,
                        ViolationReviewStatus.UNREVIEWED,
                        startedAt,
                        "track-1",
                        sessionId
                );


        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.ACTIVE
                        )
                )
        ).thenReturn(
                List.of(violation)
        );


        int result =
                service.recoverInterruptedViolations();


        assertThat(result)
                .isEqualTo(1);


        ViolationStateKey key =
                new ViolationStateKey(
                        cameraId,
                        sessionId,
                        ViolationType.MISSING_GLOVES,
                        "track-1"
                );


        assertThat(
                registry.find(key)
        )
                .contains(
                        violationId
                );
    }


    @Test
    void returnsZeroWhenNoActiveViolationsExist() {

        when(
                repository.findByLifecycleStatusIn(
                        List.of(
                                ViolationLifecycleStatus.ACTIVE
                        )
                )
        ).thenReturn(
                List.of()
        );


        assertThat(
                service.recoverInterruptedViolations()
        )
                .isZero();
    }
}