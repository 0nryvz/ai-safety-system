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
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import com.isg.backend.modules.user.service.AuthorizationService;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final AuthorizationService authorizationService;
    private final UserRepository userRepository;

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
        // 1. Giriş yapan kullanıcının bilgilerini SecurityContext'ten al
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı!"));

        // 2. Kullanıcının erişebileceği departman ID'lerini AuthorizationService'ten sorgula
        List<UUID> accessibleDeptIds = authorizationService.accessibleDepartmentIds(user.getId());

        if (accessibleDeptIds.isEmpty()) {
            return List.of();
        }

        // 3. Sadece yetkili olunan departmanlardaki kameraları getir (Metot adı düzeltildi)
        return cameraRepository.findByDepartmentIdIn(accessibleDeptIds).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CameraResponse getCameraById(UUID id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Kamera bulunamadı!"));

        // Kullanıcının bu kameranın departmanına erişim yetkisi var mı kontrol et
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı!"));

        if (!authorizationService.canAccessDepartment(user.getId(), camera.getDepartment().getId())) {
            throw new RuntimeException("Bu kameraya erişim yetkiniz yok!");
        }

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

        if (cameraSessionRepository.findBySessionId(request.getSessionId()).isPresent()) {
            return;
        }

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
                .clientInfo(request.getDeviceInfo())
                .startedAt(now)
                .status(CameraSession.SessionStatus.ACTIVE)
                .build();

        cameraSessionRepository.save(session);

        camera.setStatus(Camera.Status.ONLINE);
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

        Camera camera = session.getCamera();
        camera.setLastSeenAt(now);
        camera.setStatus(Camera.Status.ONLINE);
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
        camera.setStatus(Camera.Status.OFFLINE);
        cameraRepository.save(camera);
    }

    private CameraResponse mapToResponse(Camera camera) {
        return CameraResponse.builder()
                .id(camera.getId())
                .name(camera.getName())
                .code(camera.getCode())
                .departmentId(camera.getDepartment().getId())
                .active(camera.isActive())
                .connectionStatus(camera.getStatus() != null ? camera.getStatus().name() : "OFFLINE")
                .lastSeenAt(camera.getLastSeenAt())
                .build();
    }
}