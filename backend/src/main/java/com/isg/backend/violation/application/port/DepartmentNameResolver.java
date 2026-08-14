package com.isg.backend.violation.application.port;

import java.util.UUID;

public interface DepartmentNameResolver {

    String resolveDepartmentName(
            UUID departmentId
    );
}