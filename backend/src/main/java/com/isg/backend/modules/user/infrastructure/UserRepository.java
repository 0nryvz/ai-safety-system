package com.isg.backend.modules.user.infrastructure;

import com.isg.backend.modules.user.entity.User; // BU IMPORT EKSİK!
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> { // ID tipi Long yerine UUID oldu

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);
}