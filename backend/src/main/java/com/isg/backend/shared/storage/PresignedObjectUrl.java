package com.isg.backend.shared.storage;

import java.time.Instant;
import java.util.Objects;

public record PresignedObjectUrl(
        String url,
        Instant expiresAt
) {

    public PresignedObjectUrl {
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