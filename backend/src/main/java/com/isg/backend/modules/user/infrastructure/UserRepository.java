package com.isg.backend.modules.user.infrastructure;

import com.isg.backend.modules.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // Performans optimizasyonu: RAM yerine DB seviyesinde sayım yapar
    long countByActiveTrueAndRoles_Name(String roleName);
}