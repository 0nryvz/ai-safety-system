package com.isg.backend.modules.user.service;

import com.isg.backend.modules.user.entity.Department;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.DepartmentRepository;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Security @PreAuthorize anotasyonları ile kullanılabilmesi için
 * Bean ismini "authorizationService" olarak sabitliyoruz.
 */
@Service("authorizationService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorizationService {

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Kullanıcının belirli bir departman verisine erişip erişemeyeceğini doğrular.
     * Görev kuralı: Admin tüm bölümlere erişir.
     */
    public boolean canAccessDepartment(UUID userId, UUID departmentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı."));

        // Pasif kullanıcılar (active=false) kritik işlemlerde reddedilir.
        if (!user.isActive()) {
            return false;
        }

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ADMIN"));

        if (isAdmin) {
            return true;
        }

        if (user.getDepartments() == null || user.getDepartments().isEmpty()) {
            return false; // Departmanı olmayan normal/vardiya kullanıcısı erişemez
        }

        // Kullanıcının sahip olduğu departmanlar arasında hedef departmentId var mı kontrol et
        return user.getDepartments().stream()
                .anyMatch(dept -> dept.getId().equals(departmentId));
    }

    /**
     * Kullanıcının veri okuyabileceği departman ID'lerinin listesini döner.
     * Veritabanı sorgularında "WHERE department_id IN (...)" için kullanılır.
     */
    public List<UUID> accessibleDepartmentIds(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı."));

        if (!user.isActive()) {
            return List.of();
        }

        boolean isAdmin = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ADMIN"));

        if (isAdmin) {
            // Admin için tüm departman ID'lerini dön
            return departmentRepository.findAll().stream()
                    .map(Department::getId)
                    .collect(Collectors.toList());
        }

        if (user.getDepartments() != null && !user.getDepartments().isEmpty()) {
            // Kullanıcının atandığı tüm departmanların ID'lerini liste olarak dön
            return user.getDepartments().stream()
                    .map(Department::getId)
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}