package com.isg.backend.modules.auth.infrastructure;

import com.isg.backend.modules.auth.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    // Entity'deki alan adını 'tokenHash' olarak değiştirdiğimiz için,
    // JPA'nın doğru sorguyu atabilmesi adına metot adını da güncelledik.
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}