package com.isg.backend.violation.application;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ViolationClipGroupingProvider {

    Optional<ViolationClipGroupingView> findContext(
            UUID violationId
    );

    Set<UUID> findActiveViolationIds(
            ViolationClipGroupingView context
    );
}