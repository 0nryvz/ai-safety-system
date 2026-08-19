package com.isg.backend.modules.camera.domain; // Paket yolunuzu kendi projenize göre doğrulayın

import com.isg.backend.modules.camera.api.dto.PointDto;
import com.isg.backend.modules.camera.infrastructure.RestrictedZoneListener;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "restricted_zones")
@EntityListeners(RestrictedZoneListener.class) // Listener bağlandı
@Getter
@Setter
public class RestrictedZone {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "camera_id", nullable = false)
    private UUID cameraId;

    @Column(nullable = false, length = 120)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private List<PointDto> polygon;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Version
    private Long version;

    // Doğrudan now() kullanan metotlar RestrictedZoneListener sınıfına taşındı.
}