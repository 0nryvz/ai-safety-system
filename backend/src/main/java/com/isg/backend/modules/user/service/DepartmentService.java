package com.isg.backend.modules.user.service;

import com.isg.backend.modules.user.dto.CreateDepartmentRequest;
import com.isg.backend.modules.user.dto.DepartmentManagementResponse;
import com.isg.backend.modules.user.dto.UpdateDepartmentRequest;
import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;

    @Transactional
    public DepartmentManagementResponse createDepartment(
            CreateDepartmentRequest request
    ) {
        String normalizedCode =
                request.getCode()
                        .trim()
                        .toUpperCase(Locale.ROOT);

        String normalizedName =
                request.getName().trim();

        String normalizedDescription =
                request.getDescription() == null
                        ? null
                        : request.getDescription().trim();

        if (departmentRepository.existsByCode(normalizedCode)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bu departman kodu zaten kullanımda."
            );
        }

        if (departmentRepository.existsByName(normalizedName)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Bu departman adı zaten kullanımda."
            );
        }

        Department department =
                Department.builder()
                        .code(normalizedCode)
                        .name(normalizedName)
                        .description(normalizedDescription)
                        .active(true)
                        .build();

        Department savedDepartment =
                departmentRepository.save(department);

        return mapToResponse(savedDepartment);
    }

    @Transactional(readOnly = true)
    public List<DepartmentManagementResponse> getAllDepartments() {
        return departmentRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public DepartmentManagementResponse updateDepartment(
            UUID departmentId,
            UpdateDepartmentRequest request
    ) {
        Department department =
                departmentRepository.findById(departmentId)
                        .orElseThrow(
                                () -> new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "Departman bulunamadı."
                                )
                        );

        if (request.getName() != null) {
            String normalizedName =
                    request.getName().trim();

            if (normalizedName.isBlank()) {
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Departman adı boş olamaz."
                );
            }

            if (!normalizedName.equals(department.getName())
                    && departmentRepository.existsByName(normalizedName)) {

                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Bu departman adı zaten kullanımda."
                );
            }

            department.setName(normalizedName);
        }

        if (request.getDescription() != null) {
            department.setDescription(
                    request.getDescription().trim()
            );
        }

        if (request.getActive() != null) {
            department.setActive(
                    request.getActive()
            );
        }

        Department savedDepartment =
                departmentRepository.save(department);

        return mapToResponse(savedDepartment);
    }

    private DepartmentManagementResponse mapToResponse(
            Department department
    ) {
        return DepartmentManagementResponse.builder()
                .id(department.getId())
                .code(department.getCode())
                .name(department.getName())
                .description(department.getDescription())
                .active(department.isActive())
                .build();
    }
}