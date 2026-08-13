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
     * Görev kuralı: Admin ve İSG Uzmanı tüm bölümlere erişir; vardiya sorumlusu sadece kendi departmanına erişir[cite: 1, 2].
     */
    public boolean canAccessDepartment(UUID userId, UUID departmentId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı."));

        // Pasif kullanıcılar (active=false) kritik işlemlerde reddedilir[cite: 2].
        if (!user.isActive()) {
            return false;
        }

        // ADMIN veya OHS_SPECIALIST (İSG Uzmanı) tüm bölümlere tam erişim sağlar[cite: 1, 2]
        boolean hasFullAccess = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ADMIN") || role.getName().equals("OHS_SPECIALIST"));

        if (hasFullAccess) {
            return true;
        }

        if (user.getDepartment() == null) {
            return false; // Departmanı olmayan kullanıcı erişemez
        }

        return user.getDepartment().getId().equals(departmentId);
    }

    /**
     * Kullanıcının veri okuyabileceği departman ID'lerinin listesini döner.
     * Veritabanı sorgularında "WHERE department_id IN (...)" için kullanılır[cite: 2].
     */
    public List<UUID> accessibleDepartmentIds(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Kullanıcı bulunamadı."));

        if (!user.isActive()) {
            return List.of();
        }

        // ADMIN veya OHS_SPECIALIST (İSG Uzmanı) tüm departman ID'lerini görür[cite: 1, 2]
        boolean hasFullAccess = user.getRoles().stream()
                .anyMatch(role -> role.getName().equals("ADMIN") || role.getName().equals("OHS_SPECIALIST"));

        if (hasFullAccess) {
            return departmentRepository.findAll().stream()
                    .map(Department::getId)
                    .collect(Collectors.toList());
        }

        if (user.getDepartment() != null) {
            // Vardiya Sorumlusu yalnızca atandığı departmanı görebilir[cite: 1, 2]
            return List.of(user.getDepartment().getId());
        }

        return List.of();
    }
}