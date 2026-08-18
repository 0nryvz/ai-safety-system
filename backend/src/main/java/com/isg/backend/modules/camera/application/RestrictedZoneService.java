package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.api.dto.PointDto;
import com.isg.backend.modules.camera.api.dto.RestrictedZoneUpdateReq;
import com.isg.backend.modules.camera.domain.RestrictedZone;
import com.isg.backend.modules.camera.domain.RestrictedZoneRepository;
import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import com.isg.backend.modules.user.service.AuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus; // Eklendi
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException; // Eklendi

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RestrictedZoneService implements RestrictedZoneProvider {

    private final RestrictedZoneRepository restrictedZoneRepository;
    private final CameraRepository cameraRepository;
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

    @Transactional
    public void updateRestrictedZone(UUID cameraId, RestrictedZoneUpdateReq request) {
        // 1. Giriş yapan kullanıcıyı bul
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı!"));

        // 2. İlgili kamerayı bul (404 Not Found)
        Camera camera = cameraRepository.findById(cameraId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kamera bulunamadı!"));

        // 3. Yetki Kontrolü: 403 Forbidden standardına uyarlandı
        if (!authorizationService.canAccessDepartment(user.getId(), camera.getDepartment().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu kameranın yasaklı alanını güncelleme yetkiniz yok!");
        }

        // 4. Upsert (Varsa getir, yoksa yeni oluştur)
        RestrictedZone zone = restrictedZoneRepository.findByCameraIdAndActiveTrue(cameraId)
                .orElse(new RestrictedZone());

        if (zone.getId() == null) {
            zone.setCameraId(cameraId);
        }

        zone.setName(request.getName());
        zone.setPolygon(request.getPolygon());
        zone.setActive(true);

        restrictedZoneRepository.save(zone);
    }

    @Override
    public List<PointDto> getActivePolygonForCamera(UUID cameraId) {
        return restrictedZoneRepository.findByCameraIdAndActiveTrue(cameraId)
                .map(RestrictedZone::getPolygon)
                .orElse(List.of()); // Alan yoksa boş liste döner
    }

    public RestrictedZoneUpdateReq getRestrictedZoneDto(UUID cameraId) {
        // Aktif yasaklı alan bulunamadığında 404 Not Found dönmesi sağlandı
        RestrictedZone zone = restrictedZoneRepository.findByCameraIdAndActiveTrue(cameraId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aktif yasaklı alan bulunamadı."));

        RestrictedZoneUpdateReq response = new RestrictedZoneUpdateReq();
        response.setName(zone.getName());
        response.setPolygon(zone.getPolygon());
        return response;
    }
}