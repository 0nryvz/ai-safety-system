package com.isg.backend.violation.config;

import com.isg.backend.modules.auth.infrastructure.JwtService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;

@Component
public class WebSocketJwtChannelInterceptor
        implements ChannelInterceptor {

    private static final String AUTHORIZATION_HEADER =
            "Authorization";

    private static final String BEARER_PREFIX =
            "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public WebSocketJwtChannelInterceptor(
            JwtService jwtService,
            UserDetailsService userDetailsService
    ) {
        this.jwtService =
                jwtService;

        this.userDetailsService =
                userDetailsService;
    }

    @Override
    public Message<?> preSend(
            Message<?> message,
            MessageChannel channel
    ) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(
                        message,
                        StompHeaderAccessor.class
                );

        if (accessor == null
                || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorizationHeader =
                accessor.getFirstNativeHeader(
                        AUTHORIZATION_HEADER
                );

        if (authorizationHeader == null
                || !authorizationHeader.startsWith(
                BEARER_PREFIX
        )) {
            throw new MessagingException(
                    "Missing or invalid Authorization header."
            );
        }

        String token =
                authorizationHeader.substring(
                        BEARER_PREFIX.length()
                );

        try {
            String username =
                    jwtService.extractUsername(
                            token
                    );

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(
                            username
                    );

            if (!userDetails.isEnabled()
                    || !jwtService.isTokenValid(
                    token,
                    userDetails
            )) {
                throw new MessagingException(
                        "Invalid or expired JWT."
                );
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            accessor.setUser(
                    authentication
            );

            return message;

        } catch (MessagingException exception) {
            throw exception;

        } catch (Exception exception) {
            throw new MessagingException(
                    "WebSocket authentication failed.",
                    exception
            );
        }
    }
}