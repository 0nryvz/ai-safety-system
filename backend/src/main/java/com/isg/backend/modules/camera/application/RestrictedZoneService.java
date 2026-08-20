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

    private static final double GEOMETRY_EPSILON = 1e-9;

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

        // Polygon geometrik olarak geçerli olmalı.
        validatePolygon(request.getPolygon());

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

    private void validatePolygon(List<PointDto> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return;
        }

        int edgeCount = polygon.size();

        for (int firstEdge = 0; firstEdge < edgeCount; firstEdge++) {
            PointDto a1 = polygon.get(firstEdge);
            PointDto a2 = polygon.get((firstEdge + 1) % edgeCount);

            for (int secondEdge = firstEdge + 1; secondEdge < edgeCount; secondEdge++) {
                if (areAdjacentEdges(firstEdge, secondEdge, edgeCount)) {
                    continue;
                }

                PointDto b1 = polygon.get(secondEdge);
                PointDto b2 = polygon.get((secondEdge + 1) % edgeCount);

                if (segmentsIntersect(a1, a2, b1, b2)) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "Poligon kendi kendini kesemez."
                    );
                }
            }
        }
    }

    private boolean areAdjacentEdges(int firstEdge, int secondEdge, int edgeCount) {
        return Math.abs(firstEdge - secondEdge) == 1
                || (firstEdge == 0 && secondEdge == edgeCount - 1);
    }

    private boolean segmentsIntersect(PointDto a1, PointDto a2, PointDto b1, PointDto b2) {
        double o1 = orientation(a1, a2, b1);
        double o2 = orientation(a1, a2, b2);
        double o3 = orientation(b1, b2, a1);
        double o4 = orientation(b1, b2, a2);

        if (haveOppositeSigns(o1, o2) && haveOppositeSigns(o3, o4)) {
            return true;
        }

        return (isZero(o1) && isOnSegment(a1, b1, a2))
                || (isZero(o2) && isOnSegment(a1, b2, a2))
                || (isZero(o3) && isOnSegment(b1, a1, b2))
                || (isZero(o4) && isOnSegment(b1, a2, b2));
    }

    private double orientation(PointDto a, PointDto b, PointDto c) {
        return (b.getX() - a.getX()) * (c.getY() - a.getY())
                - (b.getY() - a.getY()) * (c.getX() - a.getX());
    }

    private boolean haveOppositeSigns(double first, double second) {
        return (first > GEOMETRY_EPSILON && second < -GEOMETRY_EPSILON)
                || (first < -GEOMETRY_EPSILON && second > GEOMETRY_EPSILON);
    }

    private boolean isZero(double value) {
        return Math.abs(value) <= GEOMETRY_EPSILON;
    }

    private boolean isOnSegment(PointDto start, PointDto point, PointDto end) {
        return point.getX() >= Math.min(start.getX(), end.getX()) - GEOMETRY_EPSILON
                && point.getX() <= Math.max(start.getX(), end.getX()) + GEOMETRY_EPSILON
                && point.getY() >= Math.min(start.getY(), end.getY()) - GEOMETRY_EPSILON
                && point.getY() <= Math.max(start.getY(), end.getY()) + GEOMETRY_EPSILON;
    }
}
