package com.isg.backend.modules.camera.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CameraResponse {
    private UUID id; // Kameranın kendi ID'si UUID olarak kalıyor
    private String name;
    private String code;
    private Long departmentId; // SADECE BURASI DEĞİŞTİ: UUID yerine Long yaptık
    private boolean active;
    private String connectionStatus; // ONLINE, WEAK, OFFLINE
    private Instant lastSeenAt;
    private String activeSessionId;
}