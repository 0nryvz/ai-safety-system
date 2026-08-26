package com.isg.backend.modules.user.service;

import com.isg.backend.modules.user.dto.CreateUserRequest;
import com.isg.backend.modules.user.dto.UpdateUserRequest;
import com.isg.backend.modules.user.dto.UserResponse;
import com.isg.backend.modules.user.dto.DepartmentResponse; // Eklendi

import java.util.List;
import java.util.UUID;

public interface UserService {
    UserResponse createUser(CreateUserRequest request);
    UserResponse updateUser(UUID id, UpdateUserRequest request, String actorEmail);
    void deactivateUser(UUID id, String actorEmail);
    UserResponse getUserById(UUID id);
    List<UserResponse> getAllUsers();
    UserResponse getMe(String email);

    // YENİ EKLENEN METOT: FE2 Talebi (Madde 1)
    List<DepartmentResponse> getMyDepartments(String email);
}