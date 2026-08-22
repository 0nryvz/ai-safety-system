package com.isg.backend.modules.user.api;

import com.isg.backend.modules.user.dto.CreateDepartmentRequest;
import com.isg.backend.modules.user.dto.DepartmentManagementResponse;
import com.isg.backend.modules.user.dto.UpdateDepartmentRequest;
import com.isg.backend.modules.user.service.DepartmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/departments")
@RequiredArgsConstructor
public class DepartmentController {

    private final DepartmentService departmentService;

    @GetMapping
    public ResponseEntity<List<DepartmentManagementResponse>> getAllDepartments() {
        return ResponseEntity.ok(
                departmentService.getAllDepartments()
        );
    }

    @PostMapping
    public ResponseEntity<DepartmentManagementResponse> createDepartment(
            @Valid @RequestBody CreateDepartmentRequest request
    ) {
        DepartmentManagementResponse response =
                departmentService.createDepartment(request);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(response);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<DepartmentManagementResponse> updateDepartment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDepartmentRequest request
    ) {
        return ResponseEntity.ok(
                departmentService.updateDepartment(id, request)
        );
    }
}