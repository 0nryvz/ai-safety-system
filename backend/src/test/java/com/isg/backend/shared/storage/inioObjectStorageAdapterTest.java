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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MinioObjectStorageAdapterTest {

    private MinioClient internalMinioClient;

    private MinioClient publicMinioClient;

    private RecordingPlaybackProperties properties;

    private MinioObjectStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        internalMinioClient =
                mock(
                        MinioClient.class
                );

        publicMinioClient =
                mock(
                        MinioClient.class
                );

        properties =
                new RecordingPlaybackProperties();

        properties.setEndpoint(
                "http://localhost:9000"
        );

        properties.setPublicEndpoint(
                "http://192.168.137.1:9000"
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
                        internalMinioClient,
                        publicMinioClient,
                        properties,
                        clock
                );
    }

    @Test
    void uploadsObjectUsingInternalMinioClient()
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
                internalMinioClient
        ).putObject(
                any(
                        PutObjectArgs.class
                )
        );

        verify(
                publicMinioClient,
                never()
        ).putObject(
                any(
                        PutObjectArgs.class
                )
        );
    }

    @Test
    void createsPresignedGetUrlUsingPublicMinioClient()
            throws Exception {

        when(
                publicMinioClient.getPresignedObjectUrl(
                        any(
                                GetPresignedObjectUrlArgs.class
                        )
                )
        ).thenReturn(
                "http://192.168.137.1:9000/violation-media/"
                        + "cameras/camera-1/reference.jpg"
                        + "?X-Amz-Expires=300"
                        + "&X-Amz-Signature=test"
        );

        PresignedObjectUrl result =
                adapter.createGetUrl(
                        "cameras/camera-1/reference.jpg"
                );

        assertThat(
                result.url()
        ).startsWith(
                "http://192.168.137.1:9000/violation-media/"
        );

        assertThat(
                result.url()
        ).contains(
                "X-Amz-Expires=300"
        );

        assertThat(
                result.url()
        ).contains(
                "X-Amz-Signature="
        );

        assertThat(
                result.expiresAt()
        ).isEqualTo(
                Instant.parse(
                        "2026-08-20T02:05:00Z"
                )
        );

        verify(
                publicMinioClient
        ).getPresignedObjectUrl(
                any(
                        GetPresignedObjectUrlArgs.class
                )
        );

        verify(
                internalMinioClient,
                never()
        ).getPresignedObjectUrl(
                any(
                        GetPresignedObjectUrlArgs.class
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
