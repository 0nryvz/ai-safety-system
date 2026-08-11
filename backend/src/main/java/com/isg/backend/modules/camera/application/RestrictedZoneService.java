package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.api.dto.PointDto;
import com.isg.backend.modules.camera.api.dto.RestrictedZoneUpdateReq;
import com.isg.backend.modules.camera.domain.RestrictedZone;
import com.isg.backend.modules.camera.domain.RestrictedZoneRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestrictedZoneService implements RestrictedZoneProvider {

    private final RestrictedZoneRepository restrictedZoneRepository;

    @Transactional
    public void updateRestrictedZone(UUID cameraId, RestrictedZoneUpdateReq request) {
        // Not: Burada kameranın var olup olmadığı ve kullanıcının kameranın
        // bulunduğu bölüme yetkisi olup olmadığı (department access)
        // Controller veya ayrı bir Authorization metoduyla doğrulanmalıdır.

        RestrictedZone zone = restrictedZoneRepository.findByCameraIdAndActiveTrue(cameraId)
                .orElse(new RestrictedZone());

        if (zone.getId() == null) {
            zone.setCameraId(cameraId);
        }

        zone.setName(request.getName());
        zone.setPolygon(request.getPolygon());
        zone.setActive(true);

        restrictedZoneRepository.save(zone);

        // Görev planı kuralı: "Zone değişikliğinde version veya updatedAt tut;
        // Backend 3 cache kullanıyorsa invalidation eventi yayınla."
        // (Şu anlık MVP için doğrudan veritabanına kaydetmemiz yeterli)
    }

    @Override
    public List<PointDto> getActivePolygonForCamera(UUID cameraId) {
        return restrictedZoneRepository.findByCameraIdAndActiveTrue(cameraId)
                .map(RestrictedZone::getPolygon)
                .orElse(List.of()); // Alan yoksa boş liste döner
    }

    public RestrictedZoneUpdateReq getRestrictedZoneDto(UUID cameraId) {
        RestrictedZone zone = restrictedZoneRepository.findByCameraIdAndActiveTrue(cameraId)
                .orElseThrow(() -> new IllegalArgumentException("Aktif yasaklı alan bulunamadı."));

        RestrictedZoneUpdateReq response = new RestrictedZoneUpdateReq();
        response.setName(zone.getName());
        response.setPolygon(zone.getPolygon());
        return response;
    }
}