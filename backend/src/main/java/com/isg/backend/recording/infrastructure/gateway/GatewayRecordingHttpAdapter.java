package com.isg.backend.recording.infrastructure.gateway;

import com.isg.backend.recording.application.StartRecordingCommand;
import com.isg.backend.recording.application.StopRecordingCommand;
import com.isg.backend.recording.application.port.GatewayRecordingCommandPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Component
@Profile("!test")
public class GatewayRecordingHttpAdapter implements GatewayRecordingCommandPort {

    private static final String START_PATH = "/internal/v1/recordings/commands/start";
    private static final String STOP_PATH = "/internal/v1/recordings/commands/stop";

    private final GatewayRecordingCommandProperties properties;
    private final RestOperations restOperations;

    @Autowired
    public GatewayRecordingHttpAdapter(
            GatewayRecordingCommandProperties properties
    ) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.getConnectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.getReadTimeout().toMillis());

        RestTemplate restTemplate = new RestTemplate(requestFactory);
        this.restOperations = restTemplate;
    }

    GatewayRecordingHttpAdapter(
            GatewayRecordingCommandProperties properties,
            RestOperations restOperations
    ) {
        this.properties = Objects.requireNonNull(properties, "properties cannot be null");
        this.restOperations = Objects.requireNonNull(restOperations, "restOperations cannot be null");
    }

    @Override
    public void sendStart(
            UUID recordingId,
            StartRecordingCommand command
    ) {
        StartGatewayRecordingRequest payload = new StartGatewayRecordingRequest(
                command.commandId(),
                recordingId,
                command.violationId(),
                command.cameraId(),
                command.sessionId(),
                command.startedAt(),
                command.preBufferSeconds(),
                command.postBufferSeconds(),
                command.maxClipSeconds()
        );

        executeWithRetry(command.commandId(), START_PATH, payload);
    }

    @Override
    public void sendStop(
            StopRecordingCommand command
    ) {
        StopGatewayRecordingRequest payload = new StopGatewayRecordingRequest(
                command.commandId(),
                command.violationId(),
                command.endedAt()
        );

        executeWithRetry(command.commandId(), STOP_PATH, payload);
    }

    private void executeWithRetry(
            UUID commandId,
            String path,
            Object payload
    ) {
        int maxRetries = Math.max(0, properties.getMaxRetries());
        String url = buildUrl(path);

        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                ResponseEntity<Void> response = restOperations.postForEntity(url, payload, Void.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    return;
                }

                throw new GatewayRecordingCommandException(
                        "Gateway returned non-success status for commandId=" + commandId
                );
            } catch (HttpClientErrorException exception) {
                if (shouldRetryClientError(exception) && attempt < maxRetries) {
                    continue;
                }

                throw new GatewayRecordingCommandException(
                        "Failed to send recording command to gateway. commandId=" + commandId,
                        exception
                );
            } catch (HttpServerErrorException | ResourceAccessException exception) {
                if (attempt < maxRetries) {
                    continue;
                }

                throw new GatewayRecordingCommandException(
                        "Failed to send recording command to gateway. commandId=" + commandId,
                        exception
                );
            }
        }

        throw new GatewayRecordingCommandException(
                "Failed to send recording command to gateway after retry limit. commandId=" + commandId
        );
    }

    private boolean shouldRetryClientError(
            HttpClientErrorException exception
    ) {
        return exception.getStatusCode().value() == 429;
    }

    private String buildUrl(
            String path
    ) {
        String baseUrl = Objects.requireNonNull(
                properties.getBaseUrl(),
                "recording.gateway.base-url must be configured"
        );

        return baseUrl.endsWith("/")
                ? baseUrl.substring(0, baseUrl.length() - 1) + path
                : baseUrl + path;
    }

    private record StartGatewayRecordingRequest(
            UUID commandId,
            UUID recordingId,
            UUID violationId,
            UUID cameraId,
            UUID sessionId,
            Instant startedAt,
            int preBufferSeconds,
            int postBufferSeconds,
            int maxClipSeconds
    ) {
    }

    private record StopGatewayRecordingRequest(
            UUID commandId,
            UUID violationId,
            Instant endedAt
    ) {
    }
}
