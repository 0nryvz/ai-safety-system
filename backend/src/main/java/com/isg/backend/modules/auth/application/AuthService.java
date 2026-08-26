package com.isg.backend.modules.auth.application;

import com.isg.backend.modules.auth.api.dto.AuthResponse;
import com.isg.backend.modules.auth.api.dto.LoginRequest;

public interface AuthService {
    AuthResponse login(LoginRequest request);

    AuthResponse refreshToken(String refreshToken);
    void logout(String refreshToken);
}