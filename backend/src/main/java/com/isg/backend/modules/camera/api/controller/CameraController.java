package com.isg.backend.modules.camera.api.controller;

import com.isg.backend.modules.camera.api.dto.CameraCreateRequest;
import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.api.dto.CameraUpdateRequest;
import com.isg.backend.modules.camera.application.CameraService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    @PostMapping
    public ResponseEntity<CameraResponse> createCamera(@RequestBody CameraCreateRequest request) {
        CameraResponse response = cameraService.createCamera(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<CameraResponse>> getAllCameras() {
        List<CameraResponse> responses = cameraService.getAllCameras();
        return ResponseEntity.ok(responses);
    }

    @GetMapping("/{id}")
    public ResponseEntity<CameraResponse> getCameraById(@PathVariable UUID id) {
        CameraResponse response = cameraService.getCameraById(id);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<CameraResponse> updateCamera(@PathVariable UUID id, @RequestBody CameraUpdateRequest request) {
        CameraResponse response = cameraService.updateCamera(id, request);
        return ResponseEntity.ok(response);
    }
}