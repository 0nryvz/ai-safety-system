package com.isg.backend.modules.camera.api.controller;

import com.isg.backend.modules.camera.api.dto.CameraCreateRequest;
import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.api.dto.CameraUpdateRequest;
import com.isg.backend.modules.camera.api.dto.ReferenceImageUrlResponse;
import com.isg.backend.modules.camera.api.dto.RestrictedZoneUpdateReq;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.modules.camera.application.ReferenceImageService;
import com.isg.backend.modules.camera.application.RestrictedZoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;
    private final RestrictedZoneService restrictedZoneService;
    private final ReferenceImageService referenceImageService;

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

    // --- Yasaklı Alan (Restricted Zone) Endpoint'leri ---

    @PutMapping("/{id}/restricted-zone")
    public ResponseEntity<Void> updateRestrictedZone(
            @PathVariable UUID id,
            @Valid @RequestBody RestrictedZoneUpdateReq request) {

        // Gelen verilerdeki PointDto validasyonları (0-1 arası sınırları ve en az 3 nokta kuralı)
        // @Valid anotasyonu sayesinde otomatik olarak kontrol edilecektir.
        restrictedZoneService.updateRestrictedZone(id, request);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/restricted-zone")
    public ResponseEntity<RestrictedZoneUpdateReq> getRestrictedZone(@PathVariable UUID id) {
        // Frontend ekibinin çizimi aynen tekrar oluşturabilmesi için
        // kaydettiğimiz koordinatları DTO formatında geri dönüyoruz.
        RestrictedZoneUpdateReq response = restrictedZoneService.getRestrictedZoneDto(id);
        return ResponseEntity.ok(response);
    }

    // --- Referans Görüntü Endpoint'leri ---

    @PostMapping("/{id}/reference-image")
    public ResponseEntity<Void> uploadReferenceImage(
            @PathVariable UUID id,
            @RequestParam("file") MultipartFile file) {

        referenceImageService.uploadReferenceImage(id, file);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{id}/reference-image-url")
    public ResponseEntity<ReferenceImageUrlResponse> getReferenceImageUrl(
            @PathVariable UUID id) {

        ReferenceImageUrlResponse response =
                referenceImageService.getReferenceImageUrl(id);

        return ResponseEntity.ok(response);
    }
}