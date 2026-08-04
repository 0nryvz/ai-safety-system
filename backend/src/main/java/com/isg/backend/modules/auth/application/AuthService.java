package com.isg.backend.modules.auth.application;

import com.isg.backend.modules.auth.api.dto.AuthResponse;
import com.isg.backend.modules.auth.api.dto.LoginRequest;
import com.isg.backend.modules.auth.api.dto.RegisterRequest;

public interface AuthService {
    AuthResponse register(RegisterRequest request);
    AuthResponse login(LoginRequest request);

    // Yeni eklenecek metotlar
    AuthResponse refreshToken(String refreshToken);
    void logout(String refreshToken);
}