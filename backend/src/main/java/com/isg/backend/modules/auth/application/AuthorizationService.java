package com.isg.backend.modules.auth.application;

import java.util.List;
import java.util.UUID; // UUID import'unu eklemeyi unutma

public interface AuthorizationService {

    boolean canAccessDepartment(UUID userId, UUID departmentId);

    List<UUID> accessibleDepartmentIds(UUID userId);
}