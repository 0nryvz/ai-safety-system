package com.isg.backend.modules.camera.domain.entity;

import com.isg.backend.modules.user.entity.Department;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "cameras")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Camera {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    // Kameranın bağlı olduğu departman. Backend 3 ve Backend 2 yetki servisleri için kritik.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    // Silme yerine pasife çekme kuralı için (Soft delete)
    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    // Heartbeat gecikmesine göre güncellenecek anlık durum (DB'deki mevcut "status" kolonu ile eşleşir)
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    @Builder.Default
    private ConnectionStatus connectionStatus = ConnectionStatus.OFFLINE;

    // Son görülme zamanı (UTC olarak tutulur)
    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    // Gateway'den gelen aktif session id (Gateway tekrar bağlandığında idempotent davranmak için)
    @Transient
    private String activeSessionId;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    // Veritabanındaki enum kısıtlamalarına (ONLINE, DEGRADED, OFFLINE) tam uyumlu
    public enum ConnectionStatus {
        ONLINE, DEGRADED, OFFLINE
    }
}