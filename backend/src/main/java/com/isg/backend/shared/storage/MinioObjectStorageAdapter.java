package com.isg.backend.shared.storage;

import com.isg.backend.recording.config.RecordingPlaybackConfig;
import com.isg.backend.recording.config.RecordingPlaybackProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Component
public class MinioObjectStorageAdapter
        implements ObjectStoragePort {

    private final MinioClient internalMinioClient;
    private final MinioClient publicMinioClient;
    private final RecordingPlaybackProperties properties;
    private final Clock clock;

    @Autowired
    public MinioObjectStorageAdapter(
            @Qualifier(RecordingPlaybackConfig.INTERNAL_MINIO_CLIENT_BEAN)
            MinioClient internalMinioClient,
            @Qualifier(RecordingPlaybackConfig.PUBLIC_MINIO_CLIENT_BEAN)
            MinioClient publicMinioClient,
            RecordingPlaybackProperties properties
    ) {
        this(
                internalMinioClient,
                publicMinioClient,
                properties,
                Clock.systemUTC()
        );
    }

    MinioObjectStorageAdapter(
            MinioClient internalMinioClient,
            MinioClient publicMinioClient,
            RecordingPlaybackProperties properties,
            Clock clock
    ) {
        this.internalMinioClient =
                Objects.requireNonNull(
                        internalMinioClient,
                        "internalMinioClient must not be null"
                );

        this.publicMinioClient =
                Objects.requireNonNull(
                        publicMinioClient,
                        "publicMinioClient must not be null"
                );

        this.properties =
                Objects.requireNonNull(
                        properties,
                        "properties must not be null"
                );

        this.clock =
                Objects.requireNonNull(
                        clock,
                        "clock must not be null"
                );
    }

    @Override
    public void putObject(
            String objectKey,
            InputStream inputStream,
            long sizeBytes,
            String contentType
    ) {
        validateObjectKey(
                objectKey
        );

        Objects.requireNonNull(
                inputStream,
                "inputStream must not be null"
        );

        if (sizeBytes <= 0) {
            throw new IllegalArgumentException(
                    "sizeBytes must be positive"
            );
        }

        Objects.requireNonNull(
                contentType,
                "contentType must not be null"
        );

        if (contentType.isBlank()) {
            throw new IllegalArgumentException(
                    "contentType must not be blank"
            );
        }

        try {
            internalMinioClient.putObject(
                    PutObjectArgs
                            .builder()
                            .bucket(
                                    properties.getBucket()
                            )
                            .object(
                                    objectKey
                            )
                            .stream(
                                    inputStream,
                                    sizeBytes,
                                    -1L
                            )
                            .contentType(
                                    contentType
                            )
                            .build()
            );
        } catch (Exception ex) {
            throw new ObjectStorageException(
                    "Failed to upload object to storage",
                    ex
            );
        }
    }

    @Override
    public PresignedObjectUrl createGetUrl(
            String objectKey
    ) {
        validateObjectKey(
                objectKey
        );

        long expirySecondsLong =
                properties
                        .getExpiry()
                        .toSeconds();

        if (
                expirySecondsLong <= 0
                        || expirySecondsLong
                        > Integer.MAX_VALUE
        ) {
            throw new IllegalStateException(
                    "object storage URL expiry is invalid"
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

            return new PresignedObjectUrl(
                    url,
                    expiresAt
            );

        } catch (Exception ex) {
            throw new ObjectStorageException(
                    "Failed to generate presigned object URL",
                    ex
            );
        }
    }

    private static void validateObjectKey(
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
    }
}
