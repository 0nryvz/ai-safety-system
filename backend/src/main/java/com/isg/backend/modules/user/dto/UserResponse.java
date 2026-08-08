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

    @Builder.Default
    private Set<Long> departmentIds = new HashSet<>(); // UUID yerine Long yapıldı

    @Builder.Default
    private Set<String> departmentNames = new HashSet<>();

    private Set<String> roles;

    private OffsetDateTime createdAt;
}