package com.isg.backend.recording.application.port;

public interface PlaybackUrlPort {

    PresignedPlaybackUrl createGetUrl(
            String objectKey
    );
}