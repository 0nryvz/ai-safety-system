package com.isg.backend.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "departments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(length = 255)
    private String description;

    // Pasife alınan departmanlardaki kullanıcıların yetkilerini kolayca dondurabilmek için
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}