package com.isg.backend.violation.config;

import com.isg.backend.modules.auth.infrastructure.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebSocketJwtChannelInterceptorTest {

    private JwtService jwtService;
    private UserDetailsService userDetailsService;
    private MessageChannel channel;
    private WebSocketJwtChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService =
                mock(JwtService.class);

        userDetailsService =
                mock(UserDetailsService.class);

        channel =
                mock(MessageChannel.class);

        interceptor =
                new WebSocketJwtChannelInterceptor(
                        jwtService,
                        userDetailsService
                );
    }

    @Test
    void authenticatesValidStompConnectAndSetsPrincipal() {
        String token =
                "valid-token";

        String email =
                "user@example.com";

        UserDetails userDetails =
                new User(
                        email,
                        "password",
                        List.of(
                                new SimpleGrantedAuthority(
                                        "ROLE_USER"
                                )
                        )
                );

        when(jwtService.extractUsername(
                token
        )).thenReturn(
                email
        );

        when(userDetailsService.loadUserByUsername(
                email
        )).thenReturn(
                userDetails
        );

        when(jwtService.isTokenValid(
                token,
                userDetails
        )).thenReturn(
                true
        );

        Message<?> message =
                connectMessage(
                        "Bearer " + token
                );

        Message<?> result =
                interceptor.preSend(
                        message,
                        channel
                );

        assertThat(result)
                .isSameAs(
                        message
                );

        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        result,
                        StompHeaderAccessor.class
                );

        assertThat(accessor)
                .isNotNull();

        assertThat(accessor.getUser())
                .isNotNull();

        assertThat(accessor.getUser().getName())
                .isEqualTo(
                        email
                );

        assertThat(accessor.getUser())
                .isInstanceOf(
                        UsernamePasswordAuthenticationToken.class
                );

        verify(jwtService)
                .extractUsername(
                        token
                );

        verify(userDetailsService)
                .loadUserByUsername(
                        email
                );

        verify(jwtService)
                .isTokenValid(
                        token,
                        userDetails
                );
    }

    @Test
    void rejectsConnectWithoutAuthorizationHeader() {
        Message<?> message =
                connectMessage(
                        null
                );

        assertThatThrownBy(
                () -> interceptor.preSend(
                        message,
                        channel
                )
        )
                .isInstanceOf(
                        MessagingException.class
                )
                .hasMessageContaining(
                        "Authorization"
                );
    }

    @Test
    void rejectsInvalidJwt() {
        String token =
                "invalid-token";

        String email =
                "user@example.com";

        UserDetails userDetails =
                User.withUsername(
                                email
                        )
                        .password(
                                "password"
                        )
                        .authorities(
                                "ROLE_USER"
                        )
                        .build();

        when(jwtService.extractUsername(
                token
        )).thenReturn(
                email
        );

        when(userDetailsService.loadUserByUsername(
                email
        )).thenReturn(
                userDetails
        );

        when(jwtService.isTokenValid(
                token,
                userDetails
        )).thenReturn(
                false
        );

        Message<?> message =
                connectMessage(
                        "Bearer " + token
                );

        assertThatThrownBy(
                () -> interceptor.preSend(
                        message,
                        channel
                )
        )
                .isInstanceOf(
                        MessagingException.class
                )
                .hasMessageContaining(
                        "Invalid or expired JWT"
                );
    }

    @Test
    void ignoresNonConnectFrames() {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.SUBSCRIBE
                );

        accessor.setLeaveMutable(
                true
        );

        Message<?> message =
                MessageBuilder.createMessage(
                        new byte[0],
                        accessor.getMessageHeaders()
                );

        Message<?> result =
                interceptor.preSend(
                        message,
                        channel
                );

        assertThat(result)
                .isSameAs(
                        message
                );
    }

    private Message<?> connectMessage(
            String authorizationHeader
    ) {
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(
                        StompCommand.CONNECT
                );

        if (authorizationHeader != null) {
            accessor.setNativeHeader(
                    "Authorization",
                    authorizationHeader
            );
        }

        accessor.setLeaveMutable(
                true
        );

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}