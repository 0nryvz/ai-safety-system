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
    private Long departmentId;
    private String departmentName;
    private Set<String> roles;

    @Builder.Default
    private Set<Long> departmentIds = new HashSet<>();

    private OffsetDateTime createdAt;
}