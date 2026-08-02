package com.isg.backend.modules.auth.infrastructure;

import com.isg.backend.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Güvenlik aşamasında, kullanıcının gönderdiği token metninden
    // veritabanındaki token nesnesini bulmak için kullanacağız.
    Optional<RefreshToken> findByToken(String token);
}