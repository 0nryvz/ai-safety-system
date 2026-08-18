package com.isg.backend.modules.auth.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private static final String API_KEY_HEADER = "X-Internal-Api-Key";
    private static final String INTERNAL_API_KEY_PROPERTY =
            "application.security.internal.api-key";

    private final Environment environment;

    public InternalApiKeyFilter(
            Environment environment
    ) {
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        if (request.getRequestURI().startsWith("/internal/")) {

            String providedKey =
                    request.getHeader(API_KEY_HEADER);

            String expectedApiKey =
                    environment.getProperty(
                            INTERNAL_API_KEY_PROPERTY
                    );

            if (
                    expectedApiKey == null
                            || expectedApiKey.isBlank()
                            || providedKey == null
                            || !providedKey.equals(expectedApiKey)
            ) {
                response.setStatus(
                        HttpServletResponse.SC_UNAUTHORIZED
                );

                response.setContentType(
                        "application/json"
                );

                response.getWriter().write(
                        "{\"error\":\"Unauthorized: "
                                + "Gecersiz veya eksik "
                                + "Internal API Key\"}"
                );

                return;
            }
        }

        filterChain.doFilter(
                request,
                response
        );
    }
}