package com.isg.backend.recording.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingPlaybackPropertiesTest {

    @Test
    void returnsExplicitPublicEndpointWhenSet() {
        RecordingPlaybackProperties properties =
                new RecordingPlaybackProperties();

        properties.setEndpoint(
                "http://localhost:9000"
        );

        properties.setPublicEndpoint(
                "http://192.168.137.1:9000"
        );

        assertThat(
                properties.getPublicEndpoint()
        ).isEqualTo(
                "http://192.168.137.1:9000"
        );

        assertThat(
                properties.getEndpoint()
        ).isEqualTo(
                "http://localhost:9000"
        );
    }

    @Test
    void fallsBackToEndpointWhenPublicEndpointIsNull() {
        RecordingPlaybackProperties properties =
                new RecordingPlaybackProperties();

        properties.setEndpoint(
                "http://localhost:9000"
        );

        properties.setPublicEndpoint(
                null
        );

        assertThat(
                properties.getPublicEndpoint()
        ).isEqualTo(
                "http://localhost:9000"
        );
    }

    @Test
    void fallsBackToEndpointWhenPublicEndpointIsBlank() {
        RecordingPlaybackProperties properties =
                new RecordingPlaybackProperties();

        properties.setEndpoint(
                "http://localhost:9000"
        );

        properties.setPublicEndpoint(
                "   "
        );

        assertThat(
                properties.getPublicEndpoint()
        ).isEqualTo(
                "http://localhost:9000"
        );
    }

    @Test
    void fallsBackToDefaultEndpointWhenPublicEndpointIsUnset() {
        RecordingPlaybackProperties properties =
                new RecordingPlaybackProperties();

        assertThat(
                properties.getPublicEndpoint()
        ).isEqualTo(
                properties.getEndpoint()
        );

        assertThat(
                properties.getPublicEndpoint()
        ).isEqualTo(
                "http://localhost:9000"
        );
    }
}
