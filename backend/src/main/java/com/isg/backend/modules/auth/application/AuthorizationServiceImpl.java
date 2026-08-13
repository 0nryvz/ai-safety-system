package com.isg.backend.modules.auth.application;

import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthorizationServiceImpl implements AuthorizationService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public boolean canAccessDepartment(UUID userId, UUID departmentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        // Admin rolü sistemdeki tüm bölümlere erişebilir
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> "ADMIN".equals(role.getName()));

        if (isAdmin) {
            return true;
        }

        // Vardiya Sorumlusu ve diğer roller sadece atandıkları bölümleri görebilir
        return user.getDepartments().stream()
                .anyMatch(dept -> dept.getId().equals(departmentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> accessibleDepartmentIds(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        return user.getDepartments().stream()
                .map(dept -> dept.getId())
                .toList();
    }
}