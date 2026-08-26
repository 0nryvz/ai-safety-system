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

    @PostMapping("/open")
    public ResponseEntity<Void> openSession(
            @Valid @RequestBody CameraSessionRequest request) {

        cameraService.openSession(request);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(
            @Valid @RequestBody CameraSessionRequest request) {

        cameraService.processHeartbeat(
                request.getCameraId(),
                request.getSessionId()
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/close")
    public ResponseEntity<Void> closeSession(
            @Valid @RequestBody CameraSessionRequest request) {

        cameraService.closeSession(
                request.getCameraId(),
                request.getSessionId()
        );

        return ResponseEntity.ok().build();
    }
}
