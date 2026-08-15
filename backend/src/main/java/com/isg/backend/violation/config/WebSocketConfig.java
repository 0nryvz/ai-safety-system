package com.isg.backend.violation.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig
        implements WebSocketMessageBrokerConfigurer {

    private final WebSocketJwtChannelInterceptor jwtChannelInterceptor;

    public WebSocketConfig(
            WebSocketJwtChannelInterceptor jwtChannelInterceptor
    ) {
        this.jwtChannelInterceptor =
                jwtChannelInterceptor;
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
                        "http://localhost:3000",
                        "http://localhost:5173"
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