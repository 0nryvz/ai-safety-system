package com.isg.backend.violation.infrastructure.camera;

import com.isg.backend.modules.camera.api.dto.PointDto;
import com.isg.backend.modules.camera.application.RestrictedZoneProvider;
import com.isg.backend.violation.application.port.RestrictedZonePort;
import com.isg.backend.violation.domain.geometry.NormalizedPoint;
import com.isg.backend.violation.domain.geometry.NormalizedPolygon;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Component
public class RestrictedZoneProviderAdapter
        implements RestrictedZonePort {

    private final RestrictedZoneProvider restrictedZoneProvider;

    public RestrictedZoneProviderAdapter(
            RestrictedZoneProvider restrictedZoneProvider
    ) {
        this.restrictedZoneProvider =
                Objects.requireNonNull(
                        restrictedZoneProvider,
                        "restrictedZoneProvider must not be null"
                );
    }

    @Override
    public Optional<NormalizedPolygon> findZone(
            UUID cameraId
    ) {
        Objects.requireNonNull(
                cameraId,
                "cameraId must not be null"
        );

        List<PointDto> points =
                restrictedZoneProvider
                        .getActivePolygonForCamera(
                                cameraId
                        );

        if (points == null || points.isEmpty()) {
            return Optional.empty();
        }

        if (points.size() < 3) {
            throw new IllegalStateException(
                    "Active restricted zone must contain at least three points."
            );
        }

        List<NormalizedPoint> normalizedPoints =
                points.stream()
                        .map(
                                point ->
                                        new NormalizedPoint(
                                                requireCoordinate(
                                                        point.getX(),
                                                        "x"
                                                ),
                                                requireCoordinate(
                                                        point.getY(),
                                                        "y"
                                                )
                                        )
                        )
                        .toList();

        return Optional.of(
                new NormalizedPolygon(
                        normalizedPoints
                )
        );
    }

    private static double requireCoordinate(
            Double coordinate,
            String name
    ) {
        if (coordinate == null) {
            throw new IllegalStateException(
                    "Restricted zone "
                            + name
                            + " coordinate must not be null."
            );
        }

        return coordinate;
    }
}