package com.isg.backend.violation.config;

import com.isg.backend.modules.auth.infrastructure.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketOriginConfigTest {

    private static final String LOCAL_WEB_ORIGIN =
            "http://localhost:5173";

    private static final String LAN_WEB_ORIGIN =
            "http://192.168.137.10:5173";

    private static final String UNCONFIGURED_ORIGIN =
            "http://evil.example";

    @Test
    void stompHandshakeUsesConfiguredLanAndLocalOrigins() {
        WebSocketProperties properties =
                new WebSocketProperties();

        properties.setAllowedOrigins(
                List.of(
                        LOCAL_WEB_ORIGIN,
                        LAN_WEB_ORIGIN
                )
        );

        String[] registeredOrigins =
                registerStompOrigins(
                        properties
                );

        assertThat(registeredOrigins)
                .containsExactly(
                        LOCAL_WEB_ORIGIN,
                        LAN_WEB_ORIGIN
                )
                .doesNotContain(
                        UNCONFIGURED_ORIGIN
                )
                .doesNotContain(
                        "*"
                );
    }

    @Test
    void stompHandshakeDoesNotRegisterUnconfiguredOrigin() {
        WebSocketProperties properties =
                new WebSocketProperties();

        properties.setAllowedOrigins(
                List.of(
                        LOCAL_WEB_ORIGIN
                )
        );

        String[] registeredOrigins =
                registerStompOrigins(
                        properties
                );

        assertThat(registeredOrigins)
                .containsExactly(
                        LOCAL_WEB_ORIGIN
                )
                .doesNotContain(
                        LAN_WEB_ORIGIN
                )
                .doesNotContain(
                        UNCONFIGURED_ORIGIN
                );
    }

    @Test
    void whitespaceAndBlankOriginsAreNormalizedBeforeHandshake() {
        WebSocketProperties properties =
                new WebSocketProperties();

        properties.setAllowedOrigins(
                List.of(
                        LOCAL_WEB_ORIGIN,
                        "  " + LAN_WEB_ORIGIN + "  ",
                        " "
                )
        );

        assertThat(properties.getAllowedOrigins())
                .containsExactly(
                        LOCAL_WEB_ORIGIN,
                        LAN_WEB_ORIGIN
                );

        String[] registeredOrigins =
                registerStompOrigins(
                        properties
                );

        assertThat(registeredOrigins)
                .containsExactly(
                        LOCAL_WEB_ORIGIN,
                        LAN_WEB_ORIGIN
                );
    }

    @Test
    void missingAllowedOriginsFailFast() {
        WebSocketProperties properties =
                new WebSocketProperties();

        WebSocketConfig config =
                new WebSocketConfig(
                        new WebSocketJwtChannelInterceptor(
                                mock(JwtService.class),
                                mock(UserDetailsService.class)
                        ),
                        properties
                );

        assertThatThrownBy(
                () -> config.registerStompEndpoints(
                        mock(StompEndpointRegistry.class)
                )
        )
                .isInstanceOf(
                        IllegalStateException.class
                )
                .hasMessageContaining(
                        "application.security.cors.allowed-origins"
                );
    }

    private String[] registerStompOrigins(
            WebSocketProperties properties
    ) {
        StompEndpointRegistry registry =
                mock(StompEndpointRegistry.class);

        StompWebSocketEndpointRegistration registration =
                mock(StompWebSocketEndpointRegistration.class);

        when(registry.addEndpoint("/ws"))
                .thenReturn(
                        registration
                );

        when(registration.setAllowedOrigins(any(String[].class)))
                .thenReturn(
                        registration
                );

        WebSocketConfig config =
                new WebSocketConfig(
                        new WebSocketJwtChannelInterceptor(
                                mock(JwtService.class),
                                mock(UserDetailsService.class)
                        ),
                        properties
                );

        config.registerStompEndpoints(
                registry
        );

        ArgumentCaptor<String[]> originsCaptor =
                ArgumentCaptor.forClass(
                        String[].class
                );

        verify(registration)
                .setAllowedOrigins(
                        originsCaptor.capture()
                );

        return originsCaptor.getValue();
    }
}
