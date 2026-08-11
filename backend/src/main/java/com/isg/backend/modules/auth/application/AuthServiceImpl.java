package com.isg.backend.modules.auth.application;

import com.isg.backend.modules.auth.api.dto.AuthResponse;
import com.isg.backend.modules.auth.api.dto.LoginRequest;
import com.isg.backend.modules.auth.entity.RefreshToken;
import com.isg.backend.modules.auth.infrastructure.JwtService;
import com.isg.backend.modules.auth.infrastructure.RefreshTokenRepository;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 1. Önce kullanıcıyı e-posta ile veritabanından buluyoruz
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Geçersiz e-posta veya şifre"));

        // 2. Şifre kontrolünden ÖNCE hesabın aktif olup olmadığını denetliyoruz
        if (!user.isActive()) {
            throw new DisabledException("Hesabınız pasif duruma alınmıştır, giriş yapılamaz.");
        }

        // 3. Kullanıcı aktifse şifre doğrulama adımını çalıştırıyoruz
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );
        } catch (Exception e) {
            System.err.println("AUTHENTICATION FAILED: " + e.getClass().getName() + " - " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // 4. Her şey yolundaysa Token'ları üretiyoruz
        String jwtToken = jwtService.generateToken(user);
        String plainRefreshToken = createAndSaveRefreshToken(user);

        return new AuthResponse(jwtToken, plainRefreshToken);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String plainRefreshToken) {
        String hashedToken = hashToken(plainRefreshToken);

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new RuntimeException("Geçersiz Refresh Token"));

        if (refreshToken.isRevoked()) {
            throw new RuntimeException("Bu oturum iptal edilmiş. Lütfen tekrar giriş yapın.");
        }

        if (refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Oturum süresi dolmuş. Lütfen tekrar giriş yapın.");
        }

        User user = refreshToken.getUser();
        String newJwt = jwtService.generateToken(user);

        return new AuthResponse(newJwt, plainRefreshToken);
    }

    @Override
    @Transactional
    public void logout(String plainRefreshToken) {
        if (plainRefreshToken == null || plainRefreshToken.isBlank()) {
            return;
        }

        String hashedToken = hashToken(plainRefreshToken);

        refreshTokenRepository.findByTokenHash(hashedToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    private String createAndSaveRefreshToken(User user) {
        String plainToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(plainToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(hashedToken)
                .user(user)
                .expiresAt(OffsetDateTime.now().plusDays(7))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return plainToken;
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Token hashlenirken hata oluştu", e);
        }
    }
}