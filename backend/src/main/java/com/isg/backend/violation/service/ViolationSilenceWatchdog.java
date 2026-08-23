package com.isg.backend.violation.service;

import com.isg.backend.violation.config.ViolationTemporalProperties;
import com.isg.backend.violation.domain.temporal.EndedViolation;
import com.isg.backend.violation.infrastructure.persistence.SpringDataViolationRepository;
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
    private final SpringDataViolationRepository violationRepository;
    private final ViolationTemporalProperties temporalProperties;

    public ViolationSilenceWatchdog(
            TemporalConfirmationService temporalConfirmationService,
            ActiveViolationRegistry activeViolationRegistry,
            ViolationLifecycleService violationLifecycleService,
            SpringDataViolationRepository violationRepository,
            ViolationTemporalProperties temporalProperties
    ) {
        this.temporalConfirmationService =
                temporalConfirmationService;

        this.activeViolationRegistry =
                activeViolationRegistry;

        this.violationLifecycleService =
                violationLifecycleService;

        this.violationRepository =
                violationRepository;

        this.temporalProperties =
                temporalProperties;
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

        endActiveViolationsForSilentClosedSessions(
                now
        );
    }

    /**
     * In-memory temporal state is lost on process restart and can also be
     * dropped if a frame-gap end is emitted before the registry mapping
     * exists. Session close already stops detections and finalizes the
     * recording clip, so the logical violation must still move out of
     * ACTIVE after the configured silence timeout.
     */
    private void endActiveViolationsForSilentClosedSessions(
            Instant now
    ) {
        Instant silentBefore =
                now.minus(
                        temporalProperties.getSilenceTimeout()
                );

        List<UUID> silentClosedViolationIds =
                violationRepository
                        .findActiveIdsForSilentClosedSessions(
                                silentBefore
                        );

        for (UUID violationId : silentClosedViolationIds) {
            try {
                violationLifecycleService.endViolation(
                        violationId,
                        now
                );

                logger.warn(
                        "Active violation ended after closed-session detection silence timeout. violationId={}, endedAt={}",
                        violationId,
                        now
                );
            } catch (RuntimeException ex) {
                logger.error(
                        "Failed to end violation after closed-session detection silence timeout. violationId={}",
                        violationId,
                        ex
                );
            }
        }
    }
}