package com.isg.backend.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.Set;

@Data
public class UpdateUserRequest {
    @NotBlank
    private String fullName;

    private Long departmentId;

    private Set<String> roleNames;

    private Boolean active;
}