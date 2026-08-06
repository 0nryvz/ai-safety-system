package com.isg.backend.violation.mapper;

import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import com.isg.backend.violation.dto.BoundingBox;
import com.isg.backend.violation.dto.DetectionItem;
import com.isg.backend.violation.dto.DetectionRequest;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DetectionMapperTest {

    private final DetectionMapper mapper =
            Mappers.getMapper(DetectionMapper.class);

    @Test
    void mapsDtoToDomainCorrectly() {
        DetectionItem item = new DetectionItem(
                "welding_mask",
                new BigDecimal("0.91"),
                new BoundingBox(
                        new BigDecimal("0.10"),
                        new BigDecimal("0.20"),
                        new BigDecimal("0.25"),
                        new BigDecimal("0.30"))
        );

        DetectionRequest dto = new DetectionRequest(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                Instant.now(),
                "model-v1",
                33L,
                List.of(item)
        );

        DetectionFrame frame = mapper.toDomain(dto);

        assertThat(frame.detections()).hasSize(1);
        assertThat(frame.detections().getFirst().label())
                .isEqualTo(DetectionLabel.WELDING_MASK);
    }
}