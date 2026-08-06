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

    // "device_info" yerine DB şeması (V3) ile uyumlu olan "client_info" olarak güncellendi
    @Column(name = "client_info")
    private String clientInfo;

    // "connected_at" yerine DB ile uyumlu olan "started_at"
    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    // "closed_at" yerine DB ile uyumlu olan "ended_at"
    @Column(name = "ended_at")
    private Instant endedAt;

    // Veritabanı ekibinin eklediği son frame (kalp atışı) zamanı
    @Column(name = "last_frame_at")
    private Instant lastFrameAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SessionStatus status = SessionStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    public enum SessionStatus {
        ACTIVE, CLOSED, TIMEOUT
    }
}