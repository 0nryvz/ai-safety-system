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

    private final Environment environment;
    private static final String API_KEY_HEADER = "X-Internal-Api-Key";

    // InternalSecurityProperties yerine Spring'in yerleşik Environment sınıfını enjekte ediyoruz
    public InternalApiKeyFilter(Environment environment) {
        this.environment = environment;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        // Sadece /internal/ ile başlayan endpointler için API Key kontrolü yap
        if (requestURI.startsWith("/internal/")) {
            String providedKey = request.getHeader(API_KEY_HEADER);

            // application.yaml veya application.properties içindeki değeri güvenle oku
            String expectedApiKey = environment.getProperty("security.internal.api-key");

            // Eğer header boşsa veya şifreyle eşleşmiyorsa isteği 401 Unauthorized ile reddet
            if (providedKey == null || !providedKey.equals(expectedApiKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized: Gecersiz veya eksik Internal API Key\"}");
                return; // Filtre zincirini burada kırıyoruz
            }
        }

        // Normal akışa devam et
        filterChain.doFilter(request, response);
    }
}