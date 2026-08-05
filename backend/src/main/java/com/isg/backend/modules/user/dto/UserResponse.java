package com.isg.backend.modules.user.dto;

import lombok.Builder;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String email;
    private String fullName;
    private boolean active;
    private Long departmentId;
    private String departmentName;
    private Set<String> roles;
    private OffsetDateTime createdAt;
}