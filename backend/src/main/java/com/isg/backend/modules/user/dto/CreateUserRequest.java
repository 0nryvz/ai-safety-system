package com.isg.backend.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty; // Eklendi
import jakarta.validation.constraints.Size;
import lombok.Data;

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

    // Tekil ID yerine artık ID listesi alıyoruz
    private Set<UUID> departmentIds;

    // Rol listesi boş veya null olamaz (Frontend ve NullPointerException koruması için)
    @NotEmpty(message = "Kullanıcı en az bir role sahip olmalıdır.")
    private Set<String> roleNames;
}