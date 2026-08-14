package com.isg.backend.recording.application.callback;

public interface RecordingStatusCallbackPort {

    void publish(
            RecordingStatusCallback callback
    );
}
