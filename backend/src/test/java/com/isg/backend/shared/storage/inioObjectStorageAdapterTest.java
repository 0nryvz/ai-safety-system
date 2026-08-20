package com.isg.backend.shared.storage;

import com.isg.backend.recording.config.RecordingPlaybackProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioObjectStorageAdapterTest {

    private MinioClient minioClient;

    private RecordingPlaybackProperties properties;

    private MinioObjectStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        minioClient =
                mock(
                        MinioClient.class
                );

        properties =
                new RecordingPlaybackProperties();

        properties.setEndpoint(
                "http://localhost:9000"
        );

        properties.setAccessKey(
                "minioadmin"
        );

        properties.setSecretKey(
                "minioadmin"
        );

        properties.setBucket(
                "violation-media"
        );

        properties.setExpiry(
                Duration.ofMinutes(5)
        );

        Clock clock =
                Clock.fixed(
                        Instant.parse(
                                "2026-08-20T02:00:00Z"
                        ),
                        ZoneOffset.UTC
                );

        adapter =
                new MinioObjectStorageAdapter(
                        minioClient,
                        properties,
                        clock
                );
    }

    @Test
    void uploadsObjectUsingConfiguredMinioClient()
            throws Exception {

        byte[] data =
                "reference-image"
                        .getBytes();

        adapter.putObject(
                "cameras/camera-1/reference.jpg",
                new ByteArrayInputStream(
                        data
                ),
                data.length,
                "image/jpeg"
        );

        verify(
                minioClient
        ).putObject(
                any(
                        PutObjectArgs.class
                )
        );
    }

    @Test
    void createsPresignedGetUrlWithConfiguredExpiry()
            throws Exception {

        when(
                minioClient.getPresignedObjectUrl(
                        any(
                                GetPresignedObjectUrlArgs.class
                        )
                )
        ).thenReturn(
                "http://localhost:9000/violation-media/"
                        + "cameras/camera-1/reference.jpg"
                        + "?X-Amz-Signature=test"
        );

        PresignedObjectUrl result =
                adapter.createGetUrl(
                        "cameras/camera-1/reference.jpg"
                );

        assertThat(
                result.url()
        ).contains(
                "reference.jpg"
        );

        assertThat(
                result.expiresAt()
        ).isEqualTo(
                Instant.parse(
                        "2026-08-20T02:05:00Z"
                )
        );
    }

    @Test
    void rejectsBlankObjectKey() {
        assertThatThrownBy(
                () ->
                        adapter.createGetUrl(
                                " "
                        )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                );
    }
}