package com.isg.backend.violation.mapper;

import com.isg.backend.violation.domain.detection.*;
import com.isg.backend.violation.dto.BoundingBox;
import com.isg.backend.violation.dto.DetectionItem;
import com.isg.backend.violation.dto.DetectionRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface DetectionMapper {

    DetectionFrame toDomain(DetectionRequest src);

    @Mapping(target = "label", source = "label", qualifiedByName = "toLabel")
    @Mapping(target = "rawLabel", source = "label")
    @Mapping(target = "boundingBox", source = "bbox")
    DetectedObject toDomain(DetectionItem src);

    // BigDecimal → double
    default com.isg.backend.violation.domain.detection.BoundingBox toDomain(BoundingBox dto) {
        return new com.isg.backend.violation.domain.detection.BoundingBox(
                dto.x().doubleValue(),
                dto.y().doubleValue(),
                dto.width().doubleValue(),
                dto.height().doubleValue()
        );
    }

    @Named("toLabel")
    default DetectionLabel toLabel(String raw) {
        return DetectionLabel.fromRawValue(raw);
    }
}