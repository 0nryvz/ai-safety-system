package com.isg.backend.modules.user.application;

import com.isg.backend.modules.user.domain.User;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder; // EKLENDİ
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;

    // Spring bizim için auth.infrastructure altındaki BCrypt nesnesini buraya bağlayacak
    private final PasswordEncoder passwordEncoder;

    @Override
    public User registerUser(String email, String password) {

        // 1. Şifreyi BCrypt ile hashliyoruz
        String hashedPassword = passwordEncoder.encode(password);

        // 2. Entity'yi oluşturuyoruz (Hashlenmiş şifreyi atıyoruz)
        com.isg.backend.modules.user.entity.User entityUser = com.isg.backend.modules.user.entity.User.builder()
                .email(email)
                .passwordHash(hashedPassword)
                .fullName(email.split("@")[0])
                .active(true)
                .build();

        // 3. Veritabanına kaydet
        com.isg.backend.modules.user.entity.User savedEntity = userRepository.save(entityUser);

        // 4. Saf Domain nesnesine çevir
        User domainUser = new User(savedEntity.getEmail(), savedEntity.getPasswordHash());
        domainUser.setId(savedEntity.getId());

        // 5. Dışarıya dön
        return domainUser;
    }
}