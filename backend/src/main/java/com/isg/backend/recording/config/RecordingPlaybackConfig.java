package com.isg.backend.recording.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecordingPlaybackConfig {

    public static final String INTERNAL_MINIO_CLIENT_BEAN =
            "recordingPlaybackInternalMinioClient";

    public static final String PUBLIC_MINIO_CLIENT_BEAN =
            "recordingPlaybackPublicMinioClient";

    @Bean(name = INTERNAL_MINIO_CLIENT_BEAN)
    public MinioClient recordingPlaybackInternalMinioClient(
            RecordingPlaybackProperties properties
    ) {
        return MinioClient.builder()
                .endpoint(
                        properties.getEndpoint()
                )
                .credentials(
                        properties.getAccessKey(),
                        properties.getSecretKey()
                )
                .build();
    }

    @Bean(name = PUBLIC_MINIO_CLIENT_BEAN)
    public MinioClient recordingPlaybackPublicMinioClient(
            RecordingPlaybackProperties properties
    ) {
        return MinioClient.builder()
                .endpoint(
                        properties.getPublicEndpoint()
                )
                .credentials(
                        properties.getAccessKey(),
                        properties.getSecretKey()
                )
                .build();
    }
}
