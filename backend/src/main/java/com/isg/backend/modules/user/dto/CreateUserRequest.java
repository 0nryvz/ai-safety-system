package com.isg.backend.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Data
public class CreateUserRequest {
    @NotBlank
    @Email
    private String email;

    @NotBlank
    @Size(min = 6)
    private String password;

    @NotBlank
    private String fullName;

    // DEĞİŞİKLİK: Tekil UUID yerine çoklu departman desteği için Set<UUID> yapıldı
    private Set<UUID> departmentIds = new HashSet<>();

    private Set<String> roleNames = new HashSet<>();
}