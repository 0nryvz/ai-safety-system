package com.isg.backend.modules.user.dto;

import lombok.Data;

import java.util.Set;
import java.util.UUID;

@Data
public class UpdateUserRequest {
    // @NotBlank kaldırıldı. Partial update'lerde fullName zorunlu olmamalı,
    // sadece gönderildiyse güncellenmelidir.
    private String fullName;

    // Tekil ID yerine artık ID listesi alıyoruz
    private Set<UUID> departmentIds;

    private Set<String> roleNames;

    private Boolean active;
}