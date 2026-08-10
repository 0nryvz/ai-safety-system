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
    // Veritabanı (Işıl'ın belirlediği) enum değerlerine göre uyarlandı
    private static final int DEGRADED_THRESHOLD_SECONDS = 20;   // 20 saniye sinyal yoksa DEGRADED
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
                // Eşiği tamamen aştıysa: Kamera bağlantısını kopar, oturumu TIMED_OUT'a çek

                // Eski setConnectionStatus yerine yeni setStatus kullanıldı
                camera.setStatus(Camera.Status.OFFLINE);
                // activeSessionId DB'de olmadığı için buradan da kaldırıldı
                cameraRepository.save(camera);

                // BURASI GÜNCELLENDİ (TIMED_OUT)
                session.setStatus(CameraSession.SessionStatus.TIMED_OUT);
                session.setEndedAt(now); // DB ile uyumlu kapanma zamanı
                cameraSessionRepository.save(session);

                // BURASI GÜNCELLENDİ (TIMED_OUT)
                log.warn("Kamera ID: {} zaman aşımına uğradı (OFFLINE). Oturum {} durumuna çekildi.",
                        camera.getId(), CameraSession.SessionStatus.TIMED_OUT);

            } else if (secondsSinceLastHeartbeat > DEGRADED_THRESHOLD_SECONDS) {
                // Sadece DEGRADED eşiğini aştıysa ve durumu henüz DEGRADED değilse:

                // Eski getConnectionStatus ve setConnectionStatus yerine yeni getStatus ve setStatus kullanıldı
                if (camera.getStatus() != Camera.Status.DEGRADED) {
                    camera.setStatus(Camera.Status.DEGRADED);
                    cameraRepository.save(camera);
                    log.info("Kamera ID: {} bağlantısı zayıfladı (DEGRADED).", camera.getId());
                }
            }
        }
    }
}