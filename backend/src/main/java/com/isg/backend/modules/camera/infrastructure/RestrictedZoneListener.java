package com.isg.backend.modules.camera.infrastructure;

import com.isg.backend.modules.camera.domain.RestrictedZone; // Doğru paket yolu ile güncellendi
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZonedDateTime;

@Component
public class RestrictedZoneListener {

    private static Clock clock;

    public RestrictedZoneListener(Clock clock) {
        RestrictedZoneListener.clock = clock;
    }

    @PrePersist
    public void prePersist(RestrictedZone zone) {
        ZonedDateTime now = ZonedDateTime.now(clock != null ? clock : Clock.systemUTC());
        zone.setCreatedAt(now);
        zone.setUpdatedAt(now);
    }

    @PreUpdate
    public void preUpdate(RestrictedZone zone) {
        zone.setUpdatedAt(ZonedDateTime.now(clock != null ? clock : Clock.systemUTC()));
    }
}