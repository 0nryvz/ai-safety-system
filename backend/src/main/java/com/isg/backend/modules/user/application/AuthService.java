package com.isg.backend.modules.user.application;

import com.isg.backend.modules.user.domain.User;


public interface AuthService {
    User registerUser(String email, String password);
}