package com.isg.backend.violation.infrastructure.notification;

import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.violation.application.port.DepartmentNameResolver;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DatabaseDepartmentNameResolver
        implements DepartmentNameResolver {

    private final DepartmentRepository departmentRepository;

    public DatabaseDepartmentNameResolver(
            DepartmentRepository departmentRepository
    ) {
        this.departmentRepository = departmentRepository;
    }

    @Override
    public String resolveDepartmentName(
            UUID departmentId
    ) {
        if (departmentId == null) {
            return "Unknown";
        }

        return departmentRepository.findById(
                        departmentId
                )
                .map(Department::getName)
                .orElse("Unknown");
    }
}