package com.isg.backend.camera.service;

// TODO (BE-2 Entegrasyonu):
// Backend-2 gerçek CameraQueryService'i tamamladığında
// FakeCameraQueryService kaldırılarak gerçek servis kullanılacak.

import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class FakeCameraQueryService implements CameraQueryService {

    @Override
    public boolean isValid(UUID cameraId, UUID sessionId) {

        return true;
    }
}
