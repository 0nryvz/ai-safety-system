package com.isg.backend.modules.camera.api.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
public class CameraResponse {
    private UUID id;
    private String name;
    private String code;
    private UUID departmentId;
    private String departmentName; // FE1 talebi doğrultusunda eklendi
    private boolean active;
    private String status; // ONLINE, WEAK, OFFLINE (Artık uyumlu)
    private Instant lastSeenAt;
    private String activeSessionId;
}