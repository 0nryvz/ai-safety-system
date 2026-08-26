package com.isg.backend.modules.auth.infrastructure;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${application.security.jwt.secret-key}")
    private String secretKey;

    @Value("${application.security.jwt.expiration}")
    private long jwtExpiration;

    @PostConstruct
    void validateConfiguration() {
        if (secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException(
                    "JWT secret must be configured as valid Base64"
            );
        }

        final byte[] keyBytes;

        try {
            keyBytes = Decoders.BASE64.decode(secretKey);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "JWT secret must be valid Base64",
                    exception
            );
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException(
                    "JWT secret must decode to at least 32 bytes"
            );
        }
    }

    // Sadece email (username) bazlı token üretimi
    public String generateToken(UserDetails userDetails) {
        return generateToken(new HashMap<>(), userDetails);
    }

    // Ekstra claim'ler (yetkiler vb.) ile token üretimi
    public String generateToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return Jwts.builder()
                .claims(extraClaims) // 0.12.x güncelemesi (setClaims yerine)
                .subject(userDetails.getUsername()) // 0.12.x güncelemesi (setSubject yerine)
                .issuedAt(new Date(System.currentTimeMillis())) // 0.12.x güncelemesi
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration)) // 0.12.x güncelemesi
                .signWith(getSignInKey()) // Algoritmayı SecretKey'in boyutundan otomatik anlar
                .compact();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser() // parserBuilder() yerine parser() geldi
                .verifyWith(getSignInKey()) // setSigningKey() yerine verifyWith() geldi
                .build()
                .parseSignedClaims(token) // parseClaimsJws() yerine parseSignedClaims() geldi
                .getPayload(); // getBody() yerine getPayload() geldi
    }

    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        // Yeni sürüm doğrudan SecretKey dönüyor, java.security.Key dönüştürmesine gerek kalmadı
        return Keys.hmacShaKeyFor(keyBytes);
    }
}