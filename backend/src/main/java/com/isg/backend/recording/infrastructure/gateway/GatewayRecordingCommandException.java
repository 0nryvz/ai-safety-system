package com.isg.backend.recording.infrastructure.gateway;

public class GatewayRecordingCommandException extends RuntimeException {

    public GatewayRecordingCommandException(
            String message
    ) {
        super(message);
    }

    public GatewayRecordingCommandException(
            String message,
            Throwable cause
    ) {
        super(message, cause);
    }
}
