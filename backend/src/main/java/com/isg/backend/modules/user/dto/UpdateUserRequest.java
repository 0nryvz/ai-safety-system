package com.isg.backend.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class UpdateUserRequest {
    @NotBlank
    private String fullName;

    private UUID departmentId;

    private Set<String> roleNames;

    private Boolean active;
}