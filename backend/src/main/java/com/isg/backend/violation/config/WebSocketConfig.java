package com.isg.backend.violation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Optional;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtChannelInterceptor jwtChannelInterceptor;
    private final WebSocketProperties websocketProperties;

    public WebSocketConfig(
            WebSocketJwtChannelInterceptor jwtChannelInterceptor,
            WebSocketProperties websocketProperties
    ) {
        this.jwtChannelInterceptor =
                jwtChannelInterceptor;

        this.websocketProperties =
                websocketProperties;
    }

    @Override
    public void configureMessageBroker(
            MessageBrokerRegistry registry
    ) {
        registry.enableSimpleBroker(
                "/queue"
        );

        registry.setApplicationDestinationPrefixes(
                "/app"
        );

        registry.setUserDestinationPrefix(
                "/user"
        );
    }

    @Override
    public void registerStompEndpoints(
            StompEndpointRegistry registry
    ) {
        registry.addEndpoint(
                        "/ws"
                )
                .setAllowedOrigins(
                        Optional.ofNullable(
                                        websocketProperties.getAllowedOrigins()
                                )
                                .orElseThrow(
                                        () -> new IllegalStateException(
                                                "application.security.cors.allowed-origins must be configured"
                                        )
                                )
                                .toArray(new String[0])
                );
    }

    @Override
    public void configureClientInboundChannel(
            ChannelRegistration registration
    ) {
        registration.interceptors(
                jwtChannelInterceptor
        );
    }
}