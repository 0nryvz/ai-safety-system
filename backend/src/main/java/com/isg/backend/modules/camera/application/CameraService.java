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
    private final Clock clock;

    @Transactional
    public CameraResponse createCamera(CameraCreateRequest request) {
        Department department = departmentRepository.findById(request.getDepartmentId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Departman bulunamadı!"
                        )
                );

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
        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Kullanıcı bulunamadı!"
                        )
                );

        List<UUID> accessibleDeptIds =
                authorizationService.accessibleDepartmentIds(user.getId());

        if (accessibleDeptIds.isEmpty()) {
            return List.of();
        }

        return cameraRepository.findByDepartmentIdIn(accessibleDeptIds)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CameraResponse getCameraById(UUID id) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Kamera bulunamadı!"
                        )
                );

        String email = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Kullanıcı bulunamadı!"
                        )
                );

        if (!authorizationService.canAccessDepartment(
                user.getId(),
                camera.getDepartment().getId())) {

            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Bu kameraya erişim yetkiniz yok!"
            );
        }

        return mapToResponse(camera);
    }

    @Transactional
    public CameraResponse updateCamera(UUID id, CameraUpdateRequest request) {
        Camera camera = cameraRepository.findById(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Kamera bulunamadı!"
                        )
                );

        if (request.getName() != null) {
            camera.setName(request.getName());
        }

        if (request.getCode() != null) {
            camera.setCode(request.getCode());
        }

        if (request.getActive() != null) {
            camera.setActive(request.getActive());
        }

        if (request.getDepartmentId() != null) {
            Department department = departmentRepository
                    .findById(request.getDepartmentId())
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.NOT_FOUND,
                                    "Departman bulunamadı!"
                            )
                    );

            camera.setDepartment(department);
        }

        return mapToResponse(cameraRepository.save(camera));
    }

    // ==========================================
    // KAMERA OTURUM (SESSION) YÖNETİM METOTLARI
    // ==========================================

    @Transactional
    public void openSession(CameraSessionRequest request) {
        if (request.getCameraId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cameraId zorunludur!"
            );
        }

        String sessionId = normalizeSessionId(request.getSessionId());

        Camera camera = cameraRepository.findById(request.getCameraId())
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Kamera bulunamadı!"
                        )
                );

        if (!camera.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pasif kameralar için oturum açılamaz!"
            );
        }

        /*
         * Aynı sessionId daha önce kullanılmışsa:
         *
         * 1. Başka kameraya aitse -> 409
         * 2. Aynı kamera + ACTIVE ise -> idempotent retry
         * 3. CLOSED / TIMED_OUT ise -> sessionId tekrar kullanılamaz
         */
        CameraSession existingSession =
                cameraSessionRepository.findBySessionId(sessionId)
                        .orElse(null);

        if (existingSession != null) {

            if (!existingSession.getCamera()
                    .getId()
                    .equals(request.getCameraId())) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "sessionId başka bir kameraya aittir!"
                );
            }

            if (existingSession.getStatus()
                    != CameraSession.SessionStatus.ACTIVE) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Kapanmış veya zaman aşımına uğramış sessionId yeniden kullanılamaz!"
                );
            }

            /*
             * Aynı cameraId + aynı ACTIVE sessionId.
             *
             * Transient retry / duplicate open olarak kabul edilir.
             * Yeni CameraSession oluşturulmaz.
             */
            Instant now = Instant.now(clock);

            camera.setStatus(Camera.Status.ONLINE);
            camera.setLastSeenAt(now);
            cameraRepository.save(camera);

            return;
        }

        /*
         * Aynı kamera için başka bir ACTIVE session varsa,
         * mevcut session'ı sessizce kapatmak yerine conflict dönüyoruz.
         *
         * Stop -> Start durumunda mobil/Gateway yeni sessionId üretmeli.
         */
        CameraSession activeSession =
                cameraSessionRepository
                        .findByCameraIdAndStatus(
                                request.getCameraId(),
                                CameraSession.SessionStatus.ACTIVE
                        )
                        .orElse(null);

        if (activeSession != null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Kamera için zaten aktif bir oturum bulunmaktadır!"
            );
        }

        Instant now = Instant.now(clock);

        CameraSession session = CameraSession.builder()
                .sessionId(sessionId)
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
    public void processHeartbeat(UUID cameraId, String sessionId) {
        if (cameraId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cameraId zorunludur!"
            );
        }

        String normalizedSessionId =
                normalizeSessionId(sessionId);

        CameraSession session =
                cameraSessionRepository
                        .findBySessionId(normalizedSessionId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Oturum bulunamadı!"
                                )
                        );

        if (!session.getCamera()
                .getId()
                .equals(cameraId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "cameraId ile sessionId eşleşmiyor!"
            );
        }

        if (session.getStatus()
                != CameraSession.SessionStatus.ACTIVE) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bu oturum aktif değil!"
            );
        }

        Camera camera = session.getCamera();

        if (!camera.isActive()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Pasif kamera için heartbeat kabul edilemez!"
            );
        }

        Instant now = Instant.now(clock);

        camera.setLastSeenAt(now);
        camera.setStatus(Camera.Status.ONLINE);

        cameraRepository.save(camera);
    }

    @Transactional
    public void closeSession(UUID cameraId, String sessionId) {
        if (cameraId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "cameraId zorunludur!"
            );
        }

        String normalizedSessionId =
                normalizeSessionId(sessionId);

        CameraSession session =
                cameraSessionRepository
                        .findBySessionId(normalizedSessionId)
                        .orElseThrow(() ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Oturum bulunamadı!"
                                )
                        );

        if (!session.getCamera()
                .getId()
                .equals(cameraId)) {

            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "cameraId ile sessionId eşleşmiyor!"
            );
        }

        /*
         * Close retry idempotent.
         *
         * CLOSED veya TIMED_OUT session için tekrar close gelirse
         * hata üretmiyoruz.
         *
         * Özellikle eski bir session'ın geç gelen close isteğinin
         * yeni session'ın kamera durumunu OFFLINE yapmasını önlüyoruz.
         */
        if (session.getStatus() == CameraSession.SessionStatus.CLOSED
                || session.getStatus()
                == CameraSession.SessionStatus.TIMED_OUT) {

            return;
        }

        Instant now = Instant.now(clock);

        session.setStatus(CameraSession.SessionStatus.CLOSED);
        session.setEndedAt(now);
        cameraSessionRepository.save(session);

        Camera camera = session.getCamera();

        camera.setStatus(Camera.Status.OFFLINE);
        cameraRepository.save(camera);
    }

    /*
     * session_id DB tarafında VARCHAR olarak kalır.
     *
     * Burada sadece Mobile/Gateway contract gereği UUID formatını
     * doğruluyor ve canonical lowercase UUID String haline getiriyoruz.
     *
     * Böylece DB migration gerekmez.
     */
    private String normalizeSessionId(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sessionId zorunludur!"
            );
        }

        try {
            return UUID.fromString(sessionId).toString();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "sessionId geçerli bir UUID formatında olmalıdır!"
            );
        }
    }

    private CameraResponse mapToResponse(Camera camera) {
        String connectionStatus =
                camera.getStatus() != null
                        ? camera.getStatus().name()
                        : "OFFLINE";

        return CameraResponse.builder()
                .id(camera.getId())
                .name(camera.getName())
                .code(camera.getCode())
                .departmentId(
                        camera.getDepartment() != null
                                ? camera.getDepartment().getId()
                                : null
                )
                .departmentName(
                        camera.getDepartment() != null
                                ? camera.getDepartment().getName()
                                : null
                )
                .active(camera.isActive())
                .connectionStatus(connectionStatus)
                .lastSeenAt(camera.getLastSeenAt())
                .build();
    }
}