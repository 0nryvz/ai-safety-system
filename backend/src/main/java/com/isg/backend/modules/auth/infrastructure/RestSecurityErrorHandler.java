package com.isg.backend.modules.auth.infrastructure;

import tools.jackson.databind.ObjectMapper;
import com.isg.backend.shared.web.ApiErrorResponse;
import com.isg.backend.shared.web.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;

public class RestSecurityErrorHandler
        implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public RestSecurityErrorHandler(
            ObjectMapper objectMapper,
            Clock clock
    ) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {

        writeError(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "UNAUTHORIZED",
                "Kimlik doğrulama gereklidir."
        );
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException
    ) throws IOException {

        writeError(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "FORBIDDEN",
                "Bu işlem için yetkiniz yok."
        );
    }

    private void writeError(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String message
    ) throws IOException {

        String correlationId =
                MDC.get(
                        CorrelationIdFilter.MDC_KEY
                );

        ApiErrorResponse body =
                new ApiErrorResponse(
                        Instant.now(clock),
                        status.value(),
                        code,
                        message,
                        request.getRequestURI(),
                        correlationId,
                        Map.of()
                );

        response.setStatus(
                status.value()
        );

        response.setContentType(
                MediaType.APPLICATION_JSON_VALUE
        );

        response.setCharacterEncoding(
                "UTF-8"
        );

        objectMapper.writeValue(
                response.getWriter(),
                body
        );
    }
}