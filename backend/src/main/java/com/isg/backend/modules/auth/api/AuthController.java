package com.isg.backend.modules.auth.api;

// Kendi paketindeki DTO'ları kullanıyor
import com.isg.backend.modules.auth.api.dto.LoginRequest;
import com.isg.backend.modules.auth.api.dto.RegisterRequest;

// User modülünün servisinden destek alıyoruz (Modüller arası iletişim)
import com.isg.backend.modules.user.application.AuthService;
import com.isg.backend.modules.user.domain.User;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    // User modülündeki servisi enjekte ediyoruz
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@Valid @RequestBody RegisterRequest request) {
        // İş mantığını User modülünün AuthService'ine devrediyoruz
        User savedUser = authService.registerUser(request.email(), request.password());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Kullanıcı başarıyla kaydedildi. Verilen ID: " + savedUser.getId());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // Şimdilik frontend testleri için mock (sahte) yanıt dönüyoruz
        return ResponseEntity.ok(Map.of(
                "accessToken", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.mockToken...",
                "tokenType", "Bearer",
                "user", Map.of(
                        "id", 1,
                        "email", request.email(),
                        "name", "Fuat Can", // İleride DB'den gelecek
                        "roles", List.of("USER")
                )
        ));
    }
}