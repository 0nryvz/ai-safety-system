package com.isg.backend.modules.user.service;

import com.isg.backend.modules.user.dto.CreateUserRequest;
import com.isg.backend.modules.user.dto.UpdateUserRequest;
import com.isg.backend.modules.user.dto.UserResponse;
import com.isg.backend.modules.user.dto.DepartmentResponse;
import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.entity.Role;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.modules.user.infrastructure.RoleRepository;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
    private final AuthorizationService authorizationService;

    @Override
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        // Duplicate email için 409 CONFLICT durumu sağlandı
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bu email adresi zaten kullanımda.");
        }

        // Güvenlik önlemi olarak null kontrolü (NullPointerException riskine karşı koruma)
        Set<String> rolesToAssign = (request.getRoleNames() != null) ? request.getRoleNames() : Set.of();

        Set<Role> roles = rolesToAssign.stream()
                .map(name -> roleRepository.findByName(name)
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol bulunamadı: " + name)))
                .collect(Collectors.toSet());

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName())
                .roles(roles)
                .active(true)
                .build();

        if (request.getDepartmentIds() != null && !request.getDepartmentIds().isEmpty()) {
            List<Department> departments = departmentRepository.findAllById(request.getDepartmentIds());
            user.getDepartments().addAll(departments);
        }

        user = userRepository.save(user);
        return mapToResponse(user);
    }

    @Override
    @Transactional
    public UserResponse updateUser(UUID id, UpdateUserRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı."));

        if (request.getActive() != null && !request.getActive() && isAdmin(user)) {
            checkIfLastAdmin();
        }

        // Sadece fullName dolu geldiyse güncelle (Partial Update desteği)
        if (request.getFullName() != null && !request.getFullName().isBlank()) {
            user.setFullName(request.getFullName());
        }

        // Sadece active durumu gönderildiyse güncelle
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        // Departman ID listesi gönderildiyse güncelle
        if (request.getDepartmentIds() != null) {
            user.getDepartments().clear();
            if (!request.getDepartmentIds().isEmpty()) {
                List<Department> depts = departmentRepository.findAllById(request.getDepartmentIds());
                user.getDepartments().addAll(depts);
            }
        }

        // Rol isimleri gönderildiyse güncelle
        if (request.getRoleNames() != null && !request.getRoleNames().isEmpty()) {
            Set<Role> roles = request.getRoleNames().stream()
                    .map(name -> roleRepository.findByName(name)
                            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rol bulunamadı: " + name)))
                    .collect(Collectors.toSet());
            user.setRoles(roles);
        }

        return mapToResponse(userRepository.save(user));
    }

    @Override
    @Transactional
    public void deactivateUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı."));

        if (isAdmin(user)) {
            checkIfLastAdmin();
        }

        user.setActive(false);
        userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı."));
        return mapToResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public UserResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı."));
        return mapToResponse(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DepartmentResponse> getMyDepartments(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Kullanıcı bulunamadı."));

        List<UUID> accessibleIds = authorizationService.accessibleDepartmentIds(user.getId());

        return departmentRepository.findAllById(accessibleIds).stream()
                .map(dept -> DepartmentResponse.builder()
                        .id(dept.getId())
                        .name(dept.getName())
                        .build())
                .collect(Collectors.toList());
    }

    private boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN"));
    }

    private void checkIfLastAdmin() {
        long adminCount = userRepository.findAll().stream()
                .filter(u -> u.isActive() && isAdmin(u))
                .count();

        if (adminCount <= 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sistemdeki son aktif ADMIN hesabı pasife alınamaz!");
        }
    }

    private UserResponse mapToResponse(User user) {
        Set<UUID> deptIds = user.getDepartments().stream()
                .map(Department::getId)
                .collect(Collectors.toSet());

        UUID firstDeptId = user.getDepartments().isEmpty() ? null : user.getDepartments().iterator().next().getId();
        String firstDeptName = user.getDepartments().isEmpty() ? null : user.getDepartments().iterator().next().getName();

        return UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .active(user.isActive())
                .departmentId(firstDeptId)
                .departmentName(firstDeptName)
                .departmentIds(deptIds)
                .roles(user.getRoles().stream().map(Role::getName).collect(Collectors.toSet()))
                .createdAt(user.getCreatedAt())
                .build();
    }
}