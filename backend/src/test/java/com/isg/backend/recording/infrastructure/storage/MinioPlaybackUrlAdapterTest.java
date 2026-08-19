package com.isg.backend.recording.infrastructure.storage;

import com.isg.backend.recording.application.port.PresignedPlaybackUrl;
import com.isg.backend.recording.config.RecordingPlaybackProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class MinioPlaybackUrlAdapterTest {

    @Test
    void createsPresignedGetUrlWithConfiguredExpiry() {
        RecordingPlaybackProperties properties =
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

        MinioClient minioClient =
                MinioClient.builder()
                        .endpoint(
                                properties.getEndpoint()
                        )
                        .credentials(
                                properties.getAccessKey(),
                                properties.getSecretKey()
                        )
                        .build();

        Instant now =
                Instant.parse(
                        "2026-08-18T03:00:00Z"
                );

        Clock clock =
                Clock.fixed(
                        now,
                        ZoneOffset.UTC
                );

        MinioPlaybackUrlAdapter adapter =
                new MinioPlaybackUrlAdapter(
                        minioClient,
                        properties,
                        clock
                );

        PresignedPlaybackUrl result =
                adapter.createGetUrl(
                        "violations/2026/08/test-violation/test-recording.mp4"
                );

        assertThat(
                result.url()
        ).startsWith(
                "http://localhost:9000/violation-media/"
        );

        assertThat(
                result.url()
        ).contains(
                "test-recording.mp4"
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
                now.plusSeconds(300)
        );
    }
}