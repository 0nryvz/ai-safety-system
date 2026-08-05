package com.isg.backend.modules.camera.api.controller;

import com.isg.backend.modules.camera.api.dto.CameraSessionRequest;
import com.isg.backend.modules.camera.application.CameraService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/camera-sessions")
@RequiredArgsConstructor
public class InternalCameraSessionController {

    private final CameraService cameraService;

    // Kamera pasif değilse yeni oturum açar
    @PostMapping("/open")
    public ResponseEntity<Void> openSession(@Valid @RequestBody CameraSessionRequest request) {
        cameraService.openSession(request);
        return ResponseEntity.ok().build();
    }

    // Canlılık sinyali gönderir, son görülme zamanını günceller
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody CameraSessionRequest request) {
        cameraService.processHeartbeat(request.getSessionId());
        return ResponseEntity.ok().build();
    }

    // Oturumu güvenli şekilde sonlandırır
    @PostMapping("/close")
    public ResponseEntity<Void> closeSession(@RequestBody CameraSessionRequest request) {
        cameraService.closeSession(request.getSessionId());
        return ResponseEntity.ok().build();
    }
}