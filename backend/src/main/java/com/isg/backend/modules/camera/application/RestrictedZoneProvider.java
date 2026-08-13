package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.api.dto.PointDto;
import java.util.List;
import java.util.UUID;

public interface RestrictedZoneProvider {
    /**
     * İlgili kameraya ait aktif yasaklı alan (polygon) noktalarını döndürür.
     * Alan yoksa boş liste döner.
     */
    List<PointDto> getActivePolygonForCamera(UUID cameraId);
}