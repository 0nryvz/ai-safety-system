package com.isg.backend.recording.infrastructure.storage;

public class PlaybackUrlGenerationException
        extends RuntimeException {

    public PlaybackUrlGenerationException(
            String message,
            Throwable cause
    ) {
        super(
                message,
                cause
        );
    }
}