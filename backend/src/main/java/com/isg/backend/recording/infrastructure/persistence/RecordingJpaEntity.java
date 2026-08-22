package com.isg.backend.recording.infrastructure.persistence;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recordings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordingJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "violation_id", nullable = false, unique = true)
    private UUID violationId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "error_code")
    private String errorCode;

    @Column(name = "recording_started_at")
    private Instant recordingStartedAt;

    @Column(name = "start_command_id")
    private UUID startCommandId;

    @Column(name = "stop_command_id")
    private UUID stopCommandId;
    @Column(name = "clip_group_id")
    private UUID clipGroupId;


    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
