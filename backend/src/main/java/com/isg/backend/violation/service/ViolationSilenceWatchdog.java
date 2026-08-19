package com.isg.backend.violation.service;

import com.isg.backend.violation.domain.temporal.EndedViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class ViolationSilenceWatchdog {

    private static final Logger logger =
            LoggerFactory.getLogger(
                    ViolationSilenceWatchdog.class
            );

    private final TemporalConfirmationService temporalConfirmationService;
    private final ActiveViolationRegistry activeViolationRegistry;
    private final ViolationLifecycleService violationLifecycleService;

    public ViolationSilenceWatchdog(
            TemporalConfirmationService temporalConfirmationService,
            ActiveViolationRegistry activeViolationRegistry,
            ViolationLifecycleService violationLifecycleService
    ) {
        this.temporalConfirmationService =
                temporalConfirmationService;

        this.activeViolationRegistry =
                activeViolationRegistry;

        this.violationLifecycleService =
                violationLifecycleService;
    }

    @Scheduled(
            fixedDelayString =
                    "${violation.temporal.silence-sweep-interval:1s}"
    )
    public void sweep() {
        sweepAt(
                Instant.now()
        );
    }

    void sweepAt(
            Instant now
    ) {
        List<EndedViolation> silentEnds =
                temporalConfirmationService
                        .findSilentConfirmedStates(
                                now
                        );

        for (EndedViolation endedViolation
                : silentEnds) {

            Optional<UUID> violationId =
                    activeViolationRegistry.find(
                            endedViolation.stateKey()
                    );

            if (violationId.isEmpty()) {
                logger.warn(
                        "Silent temporal violation has no active DB mapping. stateKey={}",
                        endedViolation.stateKey()
                );

                continue;
            }

            UUID persistedViolationId =
                    violationId.get();

            try {
                violationLifecycleService.endViolation(
                        persistedViolationId,
                        endedViolation.endedAt()
                );

                temporalConfirmationService
                        .acknowledgeSilentEnd(
                                endedViolation
                        );

                activeViolationRegistry.removeIfMappedTo(
                        endedViolation.stateKey(),
                        persistedViolationId
                );

                logger.warn(
                        "Active violation ended after detection silence timeout. violationId={}, stateKey={}, endedAt={}",
                        persistedViolationId,
                        endedViolation.stateKey(),
                        endedViolation.endedAt()
                );
            } catch (RuntimeException ex) {
                /*
                 * Both temporal state and registry mapping remain intact.
                 * A later scheduler execution can safely retry the persistent
                 * lifecycle transition.
                 */
                logger.error(
                        "Failed to end violation after detection silence timeout. violationId={}, stateKey={}",
                        persistedViolationId,
                        endedViolation.stateKey(),
                        ex
                );
            }
        }
    }
}