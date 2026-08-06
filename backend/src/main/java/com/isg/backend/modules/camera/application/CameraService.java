package com.isg.backend.modules.camera.application;

import com.isg.backend.modules.camera.api.dto.CameraCreateRequest;
import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.api.dto.CameraSessionRequest;
import com.isg.backend.modules.camera.api.dto.CameraUpdateRequest;
import com.isg.backend.modules.camera.domain.entity.Camera;
import com.isg.backend.modules.camera.domain.entity.CameraSession;
import com.isg.backend.modules.camera.infrastructure.repository.CameraRepository;
import com.isg.backend.modules.camera.infrastructure.repository.CameraSessionRepository;
import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraRepository cameraRepository;
    private final DepartmentRepository departmentRepository;
    private final CameraSessionRepository cameraSessionRepository;
    // private final AuthorizationService authorizationService;

    @Transactional
    public CameraResponse createCamera(CameraCreateRequest request) {
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new RuntimeException("Departman bulunamadı!"));

        Camera camera = Camera.builder()
                .name(request.getName())
                .code(request.getCode())
                .department(department)
                .build();

        Camera savedCamera = cameraRepository.save(camera);
        return mapToResponse(savedCamera);
    }

    @Transactional(readOnly = true)
    public List<CameraResponse> getAllCameras() {
        return cameraRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CameraResponse getCameraById(UUID id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kamera bulunamadı!"));
        return mapToResponse(camera);
    }

    @Transactional
    public CameraResponse updateCamera(UUID id, CameraUpdateRequest request) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kamera bulunamadı!"));

        if (request.getName() != null) camera.setName(request.getName());
        if (request.getCode() != null) camera.setCode(request.getCode());
        if (request.getActive() != null) camera.setActive(request.getActive());

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new RuntimeException("Departman bulunamadı!"));
            camera.setDepartment(department);
        }

        return mapToResponse(cameraRepository.save(camera));
    }

    // ==========================================
    // KAMERA OTURUM (SESSION) YÖNETİM METOTLARI
    // ==========================================

    @Transactional
    public void openSession(CameraSessionRequest request) {
        Camera camera = cameraRepository.findById(request.getCameraId())
                .orElseThrow(() -> new RuntimeException("Kamera bulunamadı!"));

        if (!camera.isActive()) {
            throw new RuntimeException("Pasif kameralar için oturum açılamaz!");
        }

        // Idempotency: Aynı sessionId ile tekrar istek gelirse mükerrer işlem yapma
        if (cameraSessionRepository.findBySessionId(request.getSessionId()).isPresent()) {
            return;
        }

        // Varsa kameranın eski aktif oturumunu kapat ve CLOSED durumuna çek
        cameraSessionRepository.findByCameraIdAndStatus(request.getCameraId(), CameraSession.SessionStatus.ACTIVE)
                .ifPresent(oldSession -> {
                    oldSession.setStatus(CameraSession.SessionStatus.CLOSED);
                    oldSession.setEndedAt(Instant.now());
                    cameraSessionRepository.save(oldSession);
                });

        Instant now = Instant.now();
        CameraSession session = CameraSession.builder()
                .sessionId(request.getSessionId())
                .camera(camera)
                // DÜZELTME: Entity'deki değişikliğe uygun olarak clientInfo kullanıldı
                .clientInfo(request.getDeviceInfo())
                .startedAt(now)
                .status(CameraSession.SessionStatus.ACTIVE)
                .build();

        cameraSessionRepository.save(session);

        // Kamera ana tablosundaki bağlantı durumunu güncelle
        camera.setActiveSessionId(request.getSessionId());
        camera.setConnectionStatus(Camera.ConnectionStatus.ONLINE);
        camera.setLastSeenAt(now);
        cameraRepository.save(camera);
    }

    @Transactional
    public void processHeartbeat(String sessionId) {
        CameraSession session = cameraSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Aktif oturum bulunamadı!"));

        if (session.getStatus() != CameraSession.SessionStatus.ACTIVE) {
            throw new RuntimeException("Bu oturum aktif değil!");
        }

        Instant now = Instant.now();

        session.setLastFrameAt(now);
        cameraSessionRepository.save(session);

        // Kamera son görülme zamanını ve durumunu güncelle
        Camera camera = session.getCamera();
        camera.setLastSeenAt(now);
        camera.setConnectionStatus(Camera.ConnectionStatus.ONLINE);
        cameraRepository.save(camera);
    }

    @Transactional
    public void closeSession(String sessionId) {
        CameraSession session = cameraSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new RuntimeException("Oturum bulunamadı!"));

        session.setStatus(CameraSession.SessionStatus.CLOSED);
        session.setEndedAt(Instant.now());
        cameraSessionRepository.save(session);

        Camera camera = session.getCamera();
        camera.setActiveSessionId(null);
        camera.setConnectionStatus(Camera.ConnectionStatus.OFFLINE);
        cameraRepository.save(camera);
    }

    // Entity -> DTO Dönüştürücü Metot
    private CameraResponse mapToResponse(Camera camera) {
        return CameraResponse.builder()
                .id(camera.getId())
                .name(camera.getName())
                .code(camera.getCode())
                .departmentId(camera.getDepartment().getId())
                .active(camera.isActive())
                .connectionStatus(camera.getConnectionStatus() != null ? camera.getConnectionStatus().name() : "OFFLINE")
                .lastSeenAt(camera.getLastSeenAt())
                .activeSessionId(camera.getActiveSessionId())
                .build();
    }
}