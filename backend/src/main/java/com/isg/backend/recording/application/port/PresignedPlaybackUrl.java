package com.isg.backend.recording.application.port;

import java.time.Instant;
import java.util.Objects;

public record PresignedPlaybackUrl(
        String url,
        Instant expiresAt
) {

    public PresignedPlaybackUrl {
        Objects.requireNonNull(
                url,
                "url must not be null"
        );

        if (url.isBlank()) {
            throw new IllegalArgumentException(
                    "url must not be blank"
            );
        }

        Objects.requireNonNull(
                expiresAt,
                "expiresAt must not be null"
        );
    }
}