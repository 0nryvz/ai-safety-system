package com.isg.backend.modules.user.service;

import com.isg.backend.modules.user.dto.CreateUserRequest;
import com.isg.backend.modules.user.dto.UpdateUserRequest;
import com.isg.backend.modules.user.dto.UserResponse;
import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.entity.Role;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.modules.user.infrastructure.RoleRepository;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Bu email adresi zaten kullanımda.");
        }

        Set<Department> departments = resolveDepartments(request.getDepartmentIds());

        Set<Role> roles = request.getRoleNames().stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new IllegalArgumentException("Rol bulunamadı: " + name)))
                .collect(Collectors.toSet());

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .departments(departments)
                .roles(roles)
                .active(true)
                .build();

        user = userRepository.save(user);
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı."));

        // Admin pasife alınmaya çalışılıyorsa son admin kontrolü yap
        if (request.getActive() != null && !request.getActive() && isAdmin(user)) {
            checkIfLastAdmin();
        }

        user.setFullName(request.getFullName());

        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        // Departman güncellemeleri
        Set<Department> updatedDepartments = resolveDepartments(request.getDepartmentIds());
        if (updatedDepartments != null) {
            user.setDepartments(updatedDepartments);
        }

        if (request.getRoleNames() != null && !request.getRoleNames().isEmpty()) {
            Set<Role> roles = request.getRoleNames().stream()
                    .map(name -> roleRepository.findByName(name)
                            .orElseThrow(() -> new IllegalArgumentException("Rol bulunamadı: " + name)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı."));

        if (isAdmin(user)) {
            checkIfLastAdmin();
        }

        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı."));
        return mapToResponse(user);
    }

    @Override
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public UserResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı."));
        return mapToResponse(user);
    }

    // --- Yardımcı Metotlar ---

    private Set<Department> resolveDepartments(Set<UUID> departmentIds) {
        Set<Department> departments = new HashSet<>();
        if (departmentIds != null && !departmentIds.isEmpty()) {
            departments = new HashSet<>(departmentRepository.findAllById(departmentIds));
            if (departments.size() != departmentIds.size()) {
                throw new IllegalArgumentException("Belirtilen departmanlardan biri veya birkaçı bulunamadı.");
            }
        }
        return departments;
    }

    private boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
    }

    private void checkIfLastAdmin() {
        long adminCount = userRepository.countByActiveTrueAndRoles_Name("ADMIN");

        if (adminCount <= 1) {
            throw new IllegalStateException("Sistemdeki son aktif ADMIN hesabı pasife alınamaz!");
        }
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .active(user.isActive())
                .departmentIds(user.getDepartments().stream().map(Department::getId).collect(Collectors.toSet()))
                .departmentNames(user.getDepartments().stream().map(Department::getName).collect(Collectors.toSet()))
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}