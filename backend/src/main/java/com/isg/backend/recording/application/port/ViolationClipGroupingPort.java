package com.isg.backend.recording.application.port;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ViolationClipGroupingPort {

    Optional<ViolationClipGroupingContext> findContext(
            UUID violationId
    );

    Set<UUID> findActiveViolationIds(
            ViolationClipGroupingContext context
    );
}