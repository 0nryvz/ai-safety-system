package com.isg.backend.recording.config;

import io.minio.MinioClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RecordingPlaybackConfig {

    @Bean
    public MinioClient recordingPlaybackMinioClient(
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
}