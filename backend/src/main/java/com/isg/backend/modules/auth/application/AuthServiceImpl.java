package com.isg.backend.modules.auth.application;

import com.isg.backend.modules.auth.api.dto.AuthResponse;
import com.isg.backend.modules.auth.api.dto.LoginRequest;
import com.isg.backend.modules.auth.api.dto.RegisterRequest;
import com.isg.backend.modules.auth.entity.RefreshToken;
import com.isg.backend.modules.auth.infrastructure.RefreshTokenRepository;
import com.isg.backend.modules.user.entity.Role;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.RoleRepository;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final RefreshTokenRepository refreshTokenRepository;

    @Override
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // 1. Email kontrolü
        if (userRepository.existsByEmail(request.email())) {
            throw new RuntimeException("Bu email adresi zaten kullanımda!");
        }

        // 2. Şifreyi hashle
        String hashedPassword = passwordEncoder.encode(request.password());

        // 3. Varsayılan rolü veritabanından bul
        Role defaultRole = roleRepository.findByName("ISG_EXPERT")
                .orElseThrow(() -> new RuntimeException("Varsayılan rol sistemde bulunamadı!"));

        // 4. Entity User'ı oluştur
        User entityUser = User.builder()
                .email(request.email())
                .passwordHash(hashedPassword)
                .fullName(request.firstName() + " " + request.lastName())
                .roles(Set.of(defaultRole))
                .active(true)
                .build();

        // 5. Kaydet
        userRepository.save(entityUser);

        // 6. Gerçek JWT ve Refresh Token Üretimi
        String jwtToken = jwtService.generateToken(entityUser);
        // HATA VEREN SATIR DEĞİŞTİRİLDİ: Özel oluşturduğumuz yardımcı metodu çağırıyoruz
        String plainRefreshToken = createAndSaveRefreshToken(entityUser);

        // 7. Yeni AuthResponse yapısı ile dönüş (tokenType otomatik "Bearer" olacak)
        return new AuthResponse(jwtToken, plainRefreshToken);
    }

    @Override
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 1. Spring Security AuthenticationManager ile şifre ve kullanıcı doğrulama
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.email(),
                        request.password()
                )
        );

        // 2. Kullanıcıyı DB'den çek
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new RuntimeException("Kullanıcı bulunamadı"));

        // 3. Pasif kullanıcı kontrolü
        if (!user.isActive()) {
            throw new RuntimeException("Hesabınız pasif duruma alınmıştır, giriş yapılamaz.");
        }

        // 4. Token Üretimi
        String jwtToken = jwtService.generateToken(user);
        // HATA VEREN SATIR DEĞİŞTİRİLDİ: Özel oluşturduğumuz yardımcı metodu çağırıyoruz
        String plainRefreshToken = createAndSaveRefreshToken(user);

        // 5. Yanıt Dönüşü
        return new AuthResponse(jwtToken, plainRefreshToken);
    }

    @Override
    @Transactional
    public AuthResponse refreshToken(String plainRefreshToken) {
        // 1. Gelen düz metin token'ı hashle
        String hashedToken = hashToken(plainRefreshToken);

        // 2. Veritabanında bu hash'e sahip token'ı bul
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new RuntimeException("Geçersiz Refresh Token"));

        // 3. Token iptal edilmiş mi kontrol et
        if (refreshToken.isRevoked()) {
            throw new RuntimeException("Bu oturum iptal edilmiş. Lütfen tekrar giriş yapın.");
        }

        // 4. Token süresi dolmuş mu kontrol et
        if (refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new RuntimeException("Oturum süresi dolmuş. Lütfen tekrar giriş yapın.");
        }

        // 5. Her şey geçerliyse, kullanıcıya yeni bir Access Token (JWT) üret
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

        // 1. Gelen token'ı hashle
        String hashedToken = hashToken(plainRefreshToken);

        // 2. DB'de bul ve revoke (iptal) et
        refreshTokenRepository.findByTokenHash(hashedToken)
                .ifPresent(token -> {
                    token.setRevoked(true);
                    refreshTokenRepository.save(token);
                });
    }

    /**
     * Güvenli, rastgele bir Refresh Token üretir, hashler ve veritabanına kaydeder.
     * Kullanıcıya iletilmesi için düz metin (plain text) token'ı geri döner.
     */
    private String createAndSaveRefreshToken(User user) {
        String plainToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(plainToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(hashedToken)
                .user(user)
                .expiresAt(OffsetDateTime.now().plusDays(7)) // 7 gün geçerlilik süresi
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);

        return plainToken;
    }

    /**
     * Opaque (saydam olmayan) token'lar için hızlı ve güvenli SHA-256 hash işlemi.
     */
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