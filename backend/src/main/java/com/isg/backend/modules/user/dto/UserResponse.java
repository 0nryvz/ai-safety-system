package com.isg.backend.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private boolean active;
    private UUID departmentId; // Long yerine UUID yapıldı
    private String departmentName;
    private Set<String> roles;

    @Builder.Default
    private Set<UUID> departmentIds = new HashSet<>(); // Set<Long> yerine Set<UUID> yapıldı

    private OffsetDateTime createdAt;
}