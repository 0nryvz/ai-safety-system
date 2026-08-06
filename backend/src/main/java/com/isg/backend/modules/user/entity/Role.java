package com.isg.backend.modules.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Rol adını tutacak alan (Örn: ADMIN, OHS_SPECIALIST, SHIFT_SUPERVISOR)
    @Column(nullable = false, unique = true, length = 50)
    private String name;
}