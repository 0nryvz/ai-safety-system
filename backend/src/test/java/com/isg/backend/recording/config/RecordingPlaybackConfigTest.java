package com.isg.backend.recording.config;

import com.isg.backend.recording.infrastructure.storage.MinioPlaybackUrlAdapter;
import com.isg.backend.shared.storage.MinioObjectStorageAdapter;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(
        RecordingPlaybackConfigTest.TestConfig.class
)
class RecordingPlaybackConfigTest {

    @Configuration
    @Import({
            RecordingPlaybackConfig.class,
            MinioPlaybackUrlAdapter.class,
            MinioObjectStorageAdapter.class
    })
    static class TestConfig {

        @Bean
        RecordingPlaybackProperties recordingPlaybackProperties() {
            RecordingPlaybackProperties properties =
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

            return properties;
        }
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    @Qualifier(RecordingPlaybackConfig.INTERNAL_MINIO_CLIENT_BEAN)
    private MinioClient internalMinioClient;

    @Autowired
    @Qualifier(RecordingPlaybackConfig.PUBLIC_MINIO_CLIENT_BEAN)
    private MinioClient publicMinioClient;

    @Autowired
    private MinioPlaybackUrlAdapter playbackUrlAdapter;

    @Autowired
    private MinioObjectStorageAdapter objectStorageAdapter;

    @Test
    void wiresDistinctInternalAndPublicMinioClients() {
        Map<String, MinioClient> minioClients =
                applicationContext.getBeansOfType(
                        MinioClient.class
                );

        assertThat(
                minioClients
        ).containsOnlyKeys(
                RecordingPlaybackConfig.INTERNAL_MINIO_CLIENT_BEAN,
                RecordingPlaybackConfig.PUBLIC_MINIO_CLIENT_BEAN
        );

        assertThat(
                internalMinioClient
        ).isNotNull();

        assertThat(
                publicMinioClient
        ).isNotNull();

        assertThat(
                playbackUrlAdapter
        ).isNotNull();

        assertThat(
                objectStorageAdapter
        ).isNotNull();
    }

    @Test
    void typeOnlyMinioClientLookupIsAmbiguous() {
        assertThatThrownBy(
                () ->
                        applicationContext.getBean(
                                MinioClient.class
                        )
        ).isInstanceOf(
                NoUniqueBeanDefinitionException.class
        );
    }
}
