package com.isg.backend.violation.mapper;

import com.isg.backend.violation.domain.detection.DetectedObject;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.domain.detection.DetectionLabel;
import com.isg.backend.violation.dto.BoundingBox;
import com.isg.backend.violation.dto.DetectionItem;
import com.isg.backend.violation.dto.DetectionRequest;
import com.isg.backend.violation.exception.UnsupportedDetectionLabelException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DetectionMapperTest {

    private final DetectionMapper mapper =
            new DetectionMapper();

    @Test
    void mapsDtoToDomainCorrectly() {
        DetectionItem item =
                new DetectionItem(
                        "welding_mask",
                        new BigDecimal("0.874"),
                        new BoundingBox(
                                new BigDecimal("0.10"),
                                new BigDecimal("0.20"),
                                new BigDecimal("0.25"),
                                new BigDecimal("0.30")
                        )
                );

        DetectionRequest request =
                new DetectionRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        "welding-ppe-v1",
                        40L,
                        List.of(
                                item
                        )
                );

        DetectionFrame result =
                mapper.toDomain(
                        request
                );

        assertThat(result.eventId())
                .isEqualTo(
                        request.eventId()
                );

        assertThat(result.cameraId())
                .isEqualTo(
                        request.cameraId()
                );

        assertThat(result.sessionId())
                .isEqualTo(
                        request.sessionId()
                );

        assertThat(result.detections())
                .hasSize(1);

        DetectedObject object =
                result.detections()
                        .getFirst();

        assertThat(object.label())
                .isEqualTo(
                        DetectionLabel.WELDING_MASK
                );

        assertThat(object.rawLabel())
                .isEqualTo(
                        "welding_mask"
                );

        assertThat(object.confidence())
                .isEqualTo(
                        0.874
                );

        assertThat(object.boundingBox().x())
                .isEqualTo(
                        0.10
                );

        assertThat(object.boundingBox().y())
                .isEqualTo(
                        0.20
                );

        assertThat(object.trackId())
                .isNull();
    }

    @Test
    void rejectsUnsupportedLabel() {
        DetectionItem item =
                new DetectionItem(
                        "visor_open",
                        new BigDecimal("0.90"),
                        new BoundingBox(
                                new BigDecimal("0.10"),
                                new BigDecimal("0.10"),
                                new BigDecimal("0.20"),
                                new BigDecimal("0.20")
                        )
                );

        DetectionRequest request =
                new DetectionRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        "welding-ppe-v1",
                        40L,
                        List.of(
                                item
                        )
                );

        assertThatThrownBy(
                () -> mapper.toDomain(
                        request
                )
        )
                .isInstanceOf(
                        UnsupportedDetectionLabelException.class
                )
                .hasMessageContaining(
                        "Unsupported AI detection label"
                );
    }

    @Test
    void rejectsUnknownNegativeLabel() {
        DetectionRequest request =
                new DetectionRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        "welding-ppe-v1",
                        40L,
                        List.of(
                                item(
                                        "non_mask"
                                )
                        )
                );

        assertThatThrownBy(
                () -> mapper.toDomain(
                        request
                )
        )
                .isInstanceOf(
                        UnsupportedDetectionLabelException.class
                );
    }

    @Test
    void mapsAllSupportedDetectionsInFrame() {
        DetectionRequest request =
                new DetectionRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        Instant.now(),
                        "welding-ppe-v1",
                        40L,
                        List.of(
                                item("Person"),
                                item("gloves"),
                                item("non_gloves"),
                                item("non_welding_jacket"),
                                item("non_welding_mask"),
                                item("welding"),
                                item("welding_apron"),
                                item("welding_jacket"),
                                item("welding_mask")
                        )
                );

        DetectionFrame result =
                mapper.toDomain(
                        request
                );

        assertThat(result.detections())
                .extracting(
                        DetectedObject::label
                )
                .containsExactly(
                        DetectionLabel.PERSON,
                        DetectionLabel.GLOVES,
                        DetectionLabel.NON_GLOVES,
                        DetectionLabel.NON_WELDING_JACKET,
                        DetectionLabel.NON_WELDING_MASK,
                        DetectionLabel.WELDING,
                        DetectionLabel.WELDING_APRON,
                        DetectionLabel.WELDING_JACKET,
                        DetectionLabel.WELDING_MASK
                );
    }

    private DetectionItem item(
            String label
    ) {
        return new DetectionItem(
                label,
                new BigDecimal("0.85"),
                new BoundingBox(
                        new BigDecimal("0.10"),
                        new BigDecimal("0.10"),
                        new BigDecimal("0.20"),
                        new BigDecimal("0.20")
                )
        );
    }
}