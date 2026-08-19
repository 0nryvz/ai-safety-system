package com.isg.backend.recording.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "recording.playback.minio")
public class RecordingPlaybackProperties {

    private String endpoint = "http://localhost:9000";
    private String accessKey = "minioadmin";
    private String secretKey = "minioadmin";
    private String bucket = "violation-media";
    private Duration expiry = Duration.ofMinutes(5);

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(
            String endpoint
    ) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(
            String accessKey
    ) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(
            String secretKey
    ) {
        this.secretKey = secretKey;
    }

    public String getBucket() {
        return bucket;
    }

    public void setBucket(
            String bucket
    ) {
        this.bucket = bucket;
    }

    public Duration getExpiry() {
        return expiry;
    }

    public void setExpiry(
            Duration expiry
    ) {
        if (expiry == null || expiry.isZero() || expiry.isNegative()) {
            throw new IllegalArgumentException(
                    "expiry must be positive"
            );
        }

        this.expiry = expiry;
    }
}