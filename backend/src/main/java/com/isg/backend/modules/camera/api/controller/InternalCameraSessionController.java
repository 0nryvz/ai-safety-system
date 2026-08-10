package com.isg.backend.modules.camera.api.controller;

import com.isg.backend.modules.camera.api.dto.CameraSessionRequest;
import com.isg.backend.modules.camera.application.CameraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/v1/camera-sessions")
@RequiredArgsConstructor
public class InternalCameraSessionController {

    private final CameraService cameraService;

    @PostMapping("/open")
    public ResponseEntity<Void> openSession(@RequestBody CameraSessionRequest request) {
        cameraService.openSession(request);
        return ResponseEntity.ok().build();
    }

    // @RequestParam yerine @RequestBody kullanıldı
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestBody CameraSessionRequest request) {
        // Gelen JSON'ın içinden sessionId'yi alıp servise gönderiyoruz
        cameraService.processHeartbeat(request.getSessionId());
        return ResponseEntity.ok().build();
    }

    // @RequestParam yerine @RequestBody kullanıldı
    @PostMapping("/close")
    public ResponseEntity<Void> closeSession(@RequestBody CameraSessionRequest request) {
        // Gelen JSON'ın içinden sessionId'yi alıp servise gönderiyoruz
        cameraService.closeSession(request.getSessionId());
        return ResponseEntity.ok().build();
    }
}