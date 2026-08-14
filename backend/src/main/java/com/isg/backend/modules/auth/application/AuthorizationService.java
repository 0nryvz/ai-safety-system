package com.isg.backend.modules.auth.application;

import java.util.List;
import java.util.UUID;

public interface AuthorizationService {

    // Kullanıcının belirli bir departmana erişimi olup olmadığını doğrular
    boolean canAccessDepartment(UUID userId, UUID departmentId);

    // Kullanıcının görebileceği tüm departman ID'lerini listeler
    List<UUID> accessibleDepartmentIds(UUID userId);

    // İhlal bildirimlerinin kimlere gideceğini bulmak için (Backend 3 kullanacak)
    List<String> getAuthorizedEmailsForDepartment(UUID departmentId);
}