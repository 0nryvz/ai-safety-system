package com.isg.backend.modules.user.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

// JPA Anotasyonları (Entity, Table, Column, Id) BURADA KESİNLİKLE OLMAMALI!
// Sadece Lombok anotasyonları ile getter/setter ve constructor'ları oluşturuyoruz.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    // ID tipini Entity sınıfındaki ile aynı (UUID) yaptık
    private UUID id;

    private String email;
    private String password;

    // Sadece email ve password alan özel constructor
    public User(String email, String password) {
        this.email = email;
        this.password = password;
    }
}