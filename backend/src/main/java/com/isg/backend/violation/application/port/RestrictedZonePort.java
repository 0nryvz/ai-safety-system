package com.isg.backend.violation.application.port;

import com.isg.backend.violation.domain.geometry.NormalizedPolygon;

import java.util.Optional;
import java.util.UUID;

public interface RestrictedZonePort {

    Optional<NormalizedPolygon> findZone(UUID cameraId);
}