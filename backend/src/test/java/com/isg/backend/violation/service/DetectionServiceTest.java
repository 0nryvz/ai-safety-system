package com.isg.backend.violation.service;

import com.isg.backend.camera.service.CameraQueryService;
import com.isg.backend.violation.domain.detection.DetectionFrame;
import com.isg.backend.violation.dto.BoundingBox;
import com.isg.backend.violation.dto.DetectionItem;
import com.isg.backend.violation.dto.DetectionRequest;
import com.isg.backend.violation.mapper.DetectionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DetectionServiceTest {

    private CameraQueryService cameraQueryService;
    private DetectionMapper detectionMapper;
    private DuplicateEventGuard duplicateEventGuard;
    private DetectionService detectionService;

    @BeforeEach
    void setUp() {
        cameraQueryService =
                mock(CameraQueryService.class);

        detectionMapper =
                mock(DetectionMapper.class);

        duplicateEventGuard =
                mock(DuplicateEventGuard.class);

        detectionService =
                new DetectionService(
                        cameraQueryService,
                        detectionMapper,
                        duplicateEventGuard
                );
    }

    @Test
    void validRequestIsMappedAndRegistered() {
        DetectionRequest request =
                validRequest(UUID.randomUUID());

        DetectionFrame frame =
                mock(DetectionFrame.class);

        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(true);

        when(detectionMapper.toDomain(request))
                .thenReturn(frame);

        when(frame.detections())
                .thenReturn(List.of());

        when(duplicateEventGuard.isFirstOccurrence(
                request.eventId()
        )).thenReturn(true);

        detectionService.process(request);

        verify(detectionMapper).toDomain(request);

        verify(duplicateEventGuard)
                .isFirstOccurrence(request.eventId());
    }

    @Test
    void invalidCameraOrSessionReturns404() {
        DetectionRequest request =
                validRequest(UUID.randomUUID());

        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(false);

        assertThatThrownBy(
                () -> detectionService.process(request)
        )
                .isInstanceOf(
                        ResponseStatusException.class
                )
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;

                    assertThat(
                            statusException.getStatusCode().value()
                    ).isEqualTo(404);
                });
    }

    @Test
    void duplicateEventReturns409() {
        DetectionRequest request =
                validRequest(UUID.randomUUID());

        DetectionFrame frame =
                mock(DetectionFrame.class);

        when(cameraQueryService.isValid(
                request.cameraId(),
                request.sessionId()
        )).thenReturn(true);

        when(detectionMapper.toDomain(request))
                .thenReturn(frame);

        when(duplicateEventGuard.isFirstOccurrence(
                request.eventId()
        )).thenReturn(false);

        assertThatThrownBy(
                () -> detectionService.process(request)
        )
                .isInstanceOf(
                        ResponseStatusException.class
                )
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;

                    assertThat(
                            statusException.getStatusCode().value()
                    ).isEqualTo(409);
                });
    }

    @Test
    void futureTimestampReturns422() {
        DetectionRequest request =
                requestWithTimestamp(
                        Instant.now().plusSeconds(30)
                );

        assertThatThrownBy(
                () -> detectionService.process(request)
        )
                .isInstanceOf(
                        ResponseStatusException.class
                )
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;

                    assertThat(
                            statusException.getStatusCode().value()
                    ).isEqualTo(422);
                });
    }

    @Test
    void oldTimestampReturns422() {
        DetectionRequest request =
                requestWithTimestamp(
                        Instant.now().minusSeconds(180)
                );

        assertThatThrownBy(
                () -> detectionService.process(request)
        )
                .isInstanceOf(
                        ResponseStatusException.class
                )
                .satisfies(exception -> {
                    ResponseStatusException statusException =
                            (ResponseStatusException) exception;

                    assertThat(
                            statusException.getStatusCode().value()
                    ).isEqualTo(422);
                });
    }

    private DetectionRequest validRequest(
            UUID eventId
    ) {
        return request(
                eventId,
                Instant.now()
        );
    }

    private DetectionRequest requestWithTimestamp(
            Instant timestamp
    ) {
        return request(
                UUID.randomUUID(),
                timestamp
        );
    }

    private DetectionRequest request(
            UUID eventId,
            Instant timestamp
    ) {
        return new DetectionRequest(
                eventId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                timestamp,
                "welding-ppe-v1",
                40L,
                List.of(
                        new DetectionItem(
                                "non_mask",
                                new BigDecimal("0.90"),
                                new BoundingBox(
                                        new BigDecimal("0.10"),
                                        new BigDecimal("0.10"),
                                        new BigDecimal("0.20"),
                                        new BigDecimal("0.20")
                                )
                        )
                )
        );
    }
}