package com.isg.backend.modules.user.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
public class DepartmentManagementResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private boolean active;
}