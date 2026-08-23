package com.isg.backend.recording.infrastructure.violation;

import com.isg.backend.recording.application.port.ViolationClipGroupingContext;
import com.isg.backend.recording.application.port.ViolationClipGroupingPort;
import com.isg.backend.violation.application.ViolationClipGroupingProvider;
import com.isg.backend.violation.application.ViolationClipGroupingView;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Component
public class ViolationClipGroupingBridgeAdapter
        implements ViolationClipGroupingPort {

    private final ViolationClipGroupingProvider groupingProvider;

    public ViolationClipGroupingBridgeAdapter(
            ViolationClipGroupingProvider groupingProvider
    ) {
        this.groupingProvider = Objects.requireNonNull(
                groupingProvider,
                "groupingProvider cannot be null"
        );
    }

    @Override
    public Optional<ViolationClipGroupingContext> findContext(
            UUID violationId
    ) {
        return groupingProvider.findContext(violationId)
                .map(this::toRecordingContext);
    }

    @Override
    public Set<UUID> findActiveViolationIds(
            ViolationClipGroupingContext context
    ) {
        Objects.requireNonNull(
                context,
                "context cannot be null"
        );

        return Set.copyOf(
                groupingProvider.findActiveViolationIds(
                        new ViolationClipGroupingView(
                                context.cameraId(),
                                context.cameraSessionId(),
                                context.subjectKey()
                        )
                )
        );
    }

    private ViolationClipGroupingContext toRecordingContext(
            ViolationClipGroupingView view
    ) {
        return new ViolationClipGroupingContext(
                view.cameraId(),
                view.cameraSessionId(),
                view.subjectKey()
        );
    }
}