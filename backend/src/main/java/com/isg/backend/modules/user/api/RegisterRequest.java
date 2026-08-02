package com.isg.backend.modules.user.api;

// Frontend'den gelecek JSON verisinin şablonu
public record RegisterRequest(String email, String password) {
}