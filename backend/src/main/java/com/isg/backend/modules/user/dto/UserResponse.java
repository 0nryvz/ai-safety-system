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
    private boolean active;4

    // DEĞİŞİKLİK: Tekil alanlar yerine çoklu departman setleri eklendi
    @Builder.Default
    private Set<UUID> departmentIds = new HashSet<>();

    @Builder.Default
    private Set<String> departmentNames = new HashSet<>();

    private Set<String> roles;

    private OffsetDateTime createdAt;
}