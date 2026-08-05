package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.domain.entity.CameraSession;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.camera.infrastructure.repository.CameraSessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class CameraConnectionMonitor {

    private final CameraRepository cameraRepository;
    private final CameraSessionRepository cameraSessionRepository;

    // Tolerans eşikleri (saniye cinsinden)
    // Gateway'den her 10 saniyede bir heartbeat geldiğini varsayarsak:
    private static final int WEAK_THRESHOLD_SECONDS = 20;   // 20 saniye sinyal yoksa WEAK
    private static final int OFFLINE_THRESHOLD_SECONDS = 45; // 45 saniye sinyal yoksa tamamen OFFLINE

    // Her 15 saniyede bir bu metot otomatik çalışır
    @Scheduled(fixedRate = 15000)
    @Transactional
    public void checkCameraConnections() {
        Instant now = Instant.now();

        // Veritabanını yormamak adına sadece aktif bir oturumu olan kameraları çekiyoruz
        List<CameraSession> activeSessions = cameraSessionRepository.findByStatus(CameraSession.SessionStatus.ACTIVE);

        for (CameraSession session : activeSessions) {
            Camera camera = session.getCamera();
            Instant lastSeen = camera.getLastSeenAt();

            if (lastSeen == null) continue;

            long secondsSinceLastHeartbeat = ChronoUnit.SECONDS.between(lastSeen, now);

            if (secondsSinceLastHeartbeat > OFFLINE_THRESHOLD_SECONDS) {
                // Eşiği tamamen aştıysa: Kamera bağlantısını kopar, oturumu TIMEOUT'a çek
                camera.setConnectionStatus(Camera.ConnectionStatus.OFFLINE);
                camera.setActiveSessionId(null);
                cameraRepository.save(camera);

                session.setStatus(CameraSession.SessionStatus.TIMEOUT);
                session.setClosedAt(now);
                cameraSessionRepository.save(session);

                log.warn("Kamera ID: {} zaman aşımına uğradı (OFFLINE). Oturum {} durumuna çekildi.",
                        camera.getId(), CameraSession.SessionStatus.TIMEOUT);

            } else if (secondsSinceLastHeartbeat > WEAK_THRESHOLD_SECONDS) {
                // Sadece WEAK eşiğini aştıysa ve durumu henüz WEAK değilse:
                if (camera.getConnectionStatus() != Camera.ConnectionStatus.WEAK) {
                    camera.setConnectionStatus(Camera.ConnectionStatus.WEAK);
                    cameraRepository.save(camera);
                    log.info("Kamera ID: {} bağlantısı zayıfladı (WEAK).", camera.getId());
                }
            }
        }
    }
}