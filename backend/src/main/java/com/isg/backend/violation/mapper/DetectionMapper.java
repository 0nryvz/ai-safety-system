package com.isg.backend.violation.mapper;

import com.isg.backend.violation.domain.detection.BoundingBox;
import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import com.isg.backend.violation.dto.DetectionItem;
import com.isg.backend.violation.dto.DetectionRequest;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DetectionMapper {

    public DetectionFrame toDomain(DetectionRequest request) {
        List<DetectedObject> detections = request.detections()
                .stream()
                .map(this::toDomain)
                .toList();

        return new DetectionFrame(
                request.eventId(),
                request.cameraId(),
                request.sessionId(),
                request.frameTimestamp(),
                request.modelVersion(),
                request.inferenceMs(),
                detections
        );
    }

    public DetectedObject toDomain(DetectionItem item) {
        return new DetectedObject(
                DetectionLabel.fromRawValue(item.label()),
                item.label(),
                item.confidence().doubleValue(),
                toDomain(item.bbox()),
                null
        );
    }

    public BoundingBox toDomain(
            com.isg.backend.violation.dto.BoundingBox bbox
    ) {
        return new BoundingBox(
                bbox.x().doubleValue(),
                bbox.y().doubleValue(),
                bbox.width().doubleValue(),
                bbox.height().doubleValue()
        );
    }
}