package com.isg.backend.modules.user.infrastructure;

import com.isg.backend.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // ADMIN olan veya ilgili departmana atanmış AKTİF kullanıcıların e-postalarını getirir
    @Query("SELECT DISTINCT u.email FROM User u " +
            "LEFT JOIN u.roles r " +
            "LEFT JOIN u.departments d " +
            "WHERE u.active = true AND (r.name = 'ADMIN' OR d.id = :departmentId)")
    List<String> findAuthorizedEmailsForDepartment(@Param("departmentId") UUID departmentId);
}