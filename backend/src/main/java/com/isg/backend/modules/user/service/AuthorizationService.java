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
 * Departman bazlı veri erişim yetkilerini merkezi olarak yönetir.
 *
 * Kurallar:
 * - ADMIN: tüm departmanlara erişebilir.
 * - OHS_SPECIALIST: tüm departmanlara erişebilir.
 * - SHIFT_SUPERVISOR: yalnızca atandığı departmanlara erişebilir.
 * - Diğer roller: departman verisine erişemez.
 * - Pasif kullanıcılar: departman verisine erişemez.
 */
@Service("authorizationService")
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthorizationService {

    private static final String ROLE_ADMIN =
            "ADMIN";

    private static final String ROLE_OHS_SPECIALIST =
            "OHS_SPECIALIST";

    private static final String ROLE_SHIFT_SUPERVISOR =
            "SHIFT_SUPERVISOR";

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;

    /**
     * Kullanıcının belirli bir departmana erişip erişemeyeceğini kontrol eder.
     */
    public boolean canAccessDepartment(
            UUID userId,
            UUID departmentId
    ) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Kullanıcı bulunamadı."
                        )
                );

        /*
         * Pasif kullanıcı hiçbir departman verisine erişemez.
         */
        if (!user.isActive()) {
            return false;
        }

        /*
         * ADMIN ve OHS_SPECIALIST tüm departmanlara erişebilir.
         */
        if (hasFullDepartmentAccess(user)) {
            return true;
        }

        /*
         * ADMIN/OHS dışında yalnızca SHIFT_SUPERVISOR
         * atanmış departmanlarına erişebilir.
         *
         * Böylece başka bir rolün yalnızca department ilişkisi
         * bulunduğu için yanlışlıkla kamera/veri erişimi alması
         * engellenir.
         */
        if (!hasRole(user, ROLE_SHIFT_SUPERVISOR)) {
            return false;
        }

        if (user.getDepartments() == null
                || user.getDepartments().isEmpty()) {
            return false;
        }

        return user.getDepartments()
                .stream()
                .anyMatch(department ->
                        department.getId().equals(departmentId)
                );
    }

    /**
     * Kullanıcının okuyabileceği departman ID listesini döner.
     *
     * Kamera listeleme gibi sorgularda:
     * WHERE department_id IN (...)
     * mantığı için kullanılır.
     */
    public List<UUID> accessibleDepartmentIds(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Kullanıcı bulunamadı."
                        )
                );

        /*
         * Pasif kullanıcıya hiçbir departman verilmez.
         */
        if (!user.isActive()) {
            return List.of();
        }

        /*
         * ADMIN ve OHS_SPECIALIST tüm departmanları görebilir.
         */
        if (hasFullDepartmentAccess(user)) {
            return departmentRepository.findAll()
                    .stream()
                    .map(Department::getId)
                    .collect(Collectors.toList());
        }

        /*
         * Diğer roller department atanmış olsa bile
         * erişim alamaz.
         */
        if (!hasRole(user, ROLE_SHIFT_SUPERVISOR)) {
            return List.of();
        }

        /*
         * SHIFT_SUPERVISOR yalnızca kendisine atanmış
         * departmanları görebilir.
         */
        if (user.getDepartments() == null
                || user.getDepartments().isEmpty()) {
            return List.of();
        }

        return user.getDepartments()
                .stream()
                .map(Department::getId)
                .collect(Collectors.toList());
    }

    /**
     * ADMIN veya OHS_SPECIALIST tam departman erişimine sahiptir.
     */
    private boolean hasFullDepartmentAccess(User user) {
        return hasRole(user, ROLE_ADMIN)
                || hasRole(user, ROLE_OHS_SPECIALIST);
    }

    /**
     * Kullanıcının belirtilen role sahip olup olmadığını güvenli şekilde kontrol eder.
     */
    private boolean hasRole(
            User user,
            String roleName
    ) {
        if (user.getRoles() == null
                || user.getRoles().isEmpty()) {
            return false;
        }

        return user.getRoles()
                .stream()
                .anyMatch(role ->
                        role != null
                                && roleName.equals(role.getName())
                );
    }
}
