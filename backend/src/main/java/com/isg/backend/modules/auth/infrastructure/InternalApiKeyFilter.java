package com.isg.backend.modules.auth.infrastructure; // Kendi paket yolunla değiştirmeyi unutma

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class InternalApiKeyFilter extends OncePerRequestFilter {

    private final InternalSecurityProperties properties;
    private static final String API_KEY_HEADER = "X-Internal-Api-Key"; // İstemcilerin göndereceği header adı

    // YAML'dan okuduğumuz değerleri enjekte ediyoruz
    public InternalApiKeyFilter(InternalSecurityProperties properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestURI = request.getRequestURI();

        // Sadece /internal/ ile başlayan endpointler için API Key kontrolü yap
        if (requestURI.startsWith("/internal/")) {
            String providedKey = request.getHeader(API_KEY_HEADER);

            // Eğer header boşsa veya YAML'daki şifreyle eşleşmiyorsa isteği 401 Unauthorized ile reddet
            if (providedKey == null || !providedKey.equals(properties.getApiKey())) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\": \"Unauthorized: Gecersiz veya eksik Internal API Key\"}");
                return; // Filtre zincirini burada kırıyoruz, istek controller'a ulaşmıyor
            }
        }

        // Eğer istek /internal/ değilse veya API Key doğruysa normal akışa (diğer filtrelere) devam et
        filterChain.doFilter(request, response);
    }
}