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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
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
    private final Clock clock; // Merkezi saat bean'i eklendi

    @Transactional
    public CameraResponse createCamera(CameraCreateRequest request) {
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Departman bulunamadı!"));

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
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı!"));

        List<UUID> accessibleDeptIds = authorizationService.accessibleDepartmentIds(user.getId());

        if (accessibleDeptIds.isEmpty()) {
            return List.of();
        }

        return cameraRepository.findByDepartmentIdIn(accessibleDeptIds).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CameraResponse getCameraById(UUID id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kamera bulunamadı!"));

        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı!"));

        if (!authorizationService.canAccessDepartment(user.getId(), camera.getDepartment().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Bu kameraya erişim yetkiniz yok!");
        }

        return mapToResponse(camera);
    }

    @Transactional
    public CameraResponse updateCamera(UUID id, CameraUpdateRequest request) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kamera bulunamadı!"));

        if (request.getName() != null) camera.setName(request.getName());
        if (request.getCode() != null) camera.setCode(request.getCode());
        if (request.getActive() != null) camera.setActive(request.getActive());

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository.findById(request.getDepartmentId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Departman bulunamadı!"));
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kamera bulunamadı!"));

        if (!camera.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pasif kameralar için oturum açılamaz!");
        }

        if (cameraSessionRepository.findBySessionId(request.getSessionId()).isPresent()) {
            return;
        }

        cameraSessionRepository.findByCameraIdAndStatus(request.getCameraId(), CameraSession.SessionStatus.ACTIVE)
                .ifPresent(oldSession -> {
                    oldSession.setStatus(CameraSession.SessionStatus.CLOSED);
                    oldSession.setEndedAt(Instant.now(clock));
                    cameraSessionRepository.save(oldSession);
                });

        Instant now = Instant.now(clock);
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
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Aktif oturum bulunamadı!"));

        if (session.getStatus() != CameraSession.SessionStatus.ACTIVE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bu oturum aktif değil!");
        }

        Instant now = Instant.now(clock);

        Camera camera = session.getCamera();
        camera.setLastSeenAt(now);
        camera.setStatus(Camera.Status.ONLINE);
        cameraRepository.save(camera);
    }

    @Transactional
    public void closeSession(String sessionId) {
        CameraSession session = cameraSessionRepository.findBySessionId(sessionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Oturum bulunamadı!"));

        session.setStatus(CameraSession.SessionStatus.CLOSED);
        session.setEndedAt(Instant.now(clock));
        cameraSessionRepository.save(session);

        Camera camera = session.getCamera();
        camera.setStatus(Camera.Status.OFFLINE);
        cameraRepository.save(camera);
    }

    private CameraResponse mapToResponse(Camera camera) {
        String rawStatus = camera.getStatus() != null ? camera.getStatus().name() : "OFFLINE";

        // Domain'deki DEGRADED durumunu dış contract gereği WEAK olarak mapliyoruz
        String connectionStatus = "DEGRADED".equals(rawStatus) ? "WEAK" : rawStatus;

        return CameraResponse.builder()
                .id(camera.getId())
                .name(camera.getName())
                .code(camera.getCode())
                .departmentId(camera.getDepartment() != null ? camera.getDepartment().getId() : null)
                .departmentName(camera.getDepartment() != null ? camera.getDepartment().getName() : null)
                .active(camera.isActive())
                .connectionStatus(connectionStatus)
                .lastSeenAt(camera.getLastSeenAt())
                .build();
    }
}