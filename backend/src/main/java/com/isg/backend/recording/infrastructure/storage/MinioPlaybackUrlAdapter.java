package com.isg.backend.recording.infrastructure.storage;

import com.isg.backend.recording.application.port.PlaybackUrlPort;
import com.isg.backend.recording.application.port.PresignedPlaybackUrl;
import com.isg.backend.recording.config.RecordingPlaybackConfig;
import com.isg.backend.recording.config.RecordingPlaybackProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.errors.MinioException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Component
public class MinioPlaybackUrlAdapter
        implements PlaybackUrlPort {

    private final MinioClient publicMinioClient;
    private final RecordingPlaybackProperties properties;
    private final Clock clock;

    @Autowired
    public MinioPlaybackUrlAdapter(
            @Qualifier(RecordingPlaybackConfig.PUBLIC_MINIO_CLIENT_BEAN)
            MinioClient publicMinioClient,
            RecordingPlaybackProperties properties
    ) {
        this(
                publicMinioClient,
                properties,
                Clock.systemUTC()
        );
    }

    MinioPlaybackUrlAdapter(
            MinioClient publicMinioClient,
            RecordingPlaybackProperties properties,
            Clock clock
    ) {
        this.publicMinioClient =
                Objects.requireNonNull(
                        publicMinioClient
                );

        this.properties =
                Objects.requireNonNull(
                        properties
                );

        this.clock =
                Objects.requireNonNull(
                        clock
                );
    }

    @Override
    public PresignedPlaybackUrl createGetUrl(
            String objectKey
    ) {
        Objects.requireNonNull(
                objectKey,
                "objectKey must not be null"
        );

        if (objectKey.isBlank()) {
            throw new IllegalArgumentException(
                    "objectKey must not be blank"
            );
        }

        long expirySecondsLong =
                properties
                        .getExpiry()
                        .toSeconds();

        if (expirySecondsLong <= 0
                || expirySecondsLong > Integer.MAX_VALUE) {
            throw new IllegalStateException(
                    "playback expiry is invalid"
            );
        }

        int expirySeconds =
                Math.toIntExact(
                        expirySecondsLong
                );

        try {
            String url =
                    publicMinioClient
                            .getPresignedObjectUrl(
                                    GetPresignedObjectUrlArgs
                                            .builder()
                                            .method(
                                                    Http.Method.GET
                                            )
                                            .bucket(
                                                    properties.getBucket()
                                            )
                                            .object(
                                                    objectKey
                                            )
                                            .expiry(
                                                    expirySeconds
                                            )
                                            .build()
                            );

            Instant expiresAt =
                    Instant.now(
                            clock
                    ).plusSeconds(
                            expirySeconds
                    );

            return new PresignedPlaybackUrl(
                    url,
                    expiresAt
            );
        } catch (MinioException ex) {
            throw new PlaybackUrlGenerationException(
                    "Failed to generate presigned playback URL",
                    ex
            );
        }
    }
}