package com.isg.backend.modules.camera.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "camera_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CameraSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "camera_id", nullable = false)
    private Camera camera;

    // Gateway tarafından üretilen UUID
    @Column(name = "session_id", nullable = false, unique = true)
    private String sessionId;


    // Oturumun açıldığı zaman (VERİTABANI İLE UYUMLU)
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    // Oturum kapandıysa kapanma zamanı (VERİTABANI İLE UYUMLU)
    @Column(name = "ended_at")
    private Instant endedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum SessionStatus {
        ACTIVE, CLOSED, TIMED_OUT
    }
}