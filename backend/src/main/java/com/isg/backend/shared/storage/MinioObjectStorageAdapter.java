package com.isg.backend.shared.storage;

import com.isg.backend.recording.config.RecordingPlaybackProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Component
public class MinioObjectStorageAdapter
        implements ObjectStoragePort {

    private final MinioClient minioClient;
    private final RecordingPlaybackProperties properties;
    private final Clock clock;

    @Autowired
    public MinioObjectStorageAdapter(
            MinioClient minioClient,
            RecordingPlaybackProperties properties
    ) {
        this(
                minioClient,
                properties,
                Clock.systemUTC()
        );
    }

    MinioObjectStorageAdapter(
            MinioClient minioClient,
            RecordingPlaybackProperties properties,
            Clock clock
    ) {
        this.minioClient =
                Objects.requireNonNull(
                        minioClient,
                        "minioClient must not be null"
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
            minioClient.putObject(
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
                    minioClient
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