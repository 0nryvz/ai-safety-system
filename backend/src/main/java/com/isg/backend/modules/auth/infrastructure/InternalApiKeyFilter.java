package com.isg.backend.modules.auth.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final Environment environment;
    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    public InternalApiKeyFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        // Sadece /internal/ ile başlayan endpointler için API Key kontrolü yap
        if (requestURI.startsWith("/internal/")) {
            String providedKey = request.getHeader(API_KEY_HEADER);
            String expectedApiKey = environment.getProperty("application.security.internal.api-key");

            // Eğer header boşsa veya şifreyle eşleşmiyorsa ortak ApiErrorResponse formatında 401 dön
            if (providedKey == null || !providedKey.equals(expectedApiKey)) {
                HttpStatus status = HttpStatus.UNAUTHORIZED;

                // Ortak ApiErrorResponse standartlarına uygun JSON yapısı
                String jsonResponse = String.format(
                        "{\"timestamp\":\"%s\",\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"path\":\"%s\"}",
                        Instant.now().toString(),
                        status.value(),
                        status.getReasonPhrase(),
                        "Unauthorized: Gecersiz veya eksik Internal API Key",
                        requestURI
                );

                response.setStatus(status.value());
                response.setContentType("application/json");
                response.setCharacterEncoding("UTF-8");
                response.getWriter().write(jsonResponse);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}