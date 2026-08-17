package com.isg.backend.violation.application.port;

import java.util.Optional;
import java.util.UUID;

public interface RecordingQueryPort {

    Optional<RecordingQueryResult> findByViolationId(
            UUID violationId
    );
}