package com.isg.backend.recording.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "recording.command")
public class RecordingCommandProperties {

    private int preBufferSeconds = 5;
    private int postBufferSeconds = 5;
    private int maxClipSeconds = 30;

    public int getPreBufferSeconds() {
        return preBufferSeconds;
    }

    public void setPreBufferSeconds(
            int preBufferSeconds
    ) {
        if (preBufferSeconds < 0) {
            throw new IllegalArgumentException(
                    "preBufferSeconds cannot be negative"
            );
        }

        this.preBufferSeconds = preBufferSeconds;
    }

    public int getPostBufferSeconds() {
        return postBufferSeconds;
    }

    public void setPostBufferSeconds(
            int postBufferSeconds
    ) {
        if (postBufferSeconds < 0) {
            throw new IllegalArgumentException(
                    "postBufferSeconds cannot be negative"
            );
        }

        this.postBufferSeconds = postBufferSeconds;
    }

    public int getMaxClipSeconds() {
        return maxClipSeconds;
    }

    public void setMaxClipSeconds(
            int maxClipSeconds
    ) {
        if (maxClipSeconds <= 0) {
            throw new IllegalArgumentException(
                    "maxClipSeconds must be positive"
            );
        }

        this.maxClipSeconds = maxClipSeconds;
    }
}