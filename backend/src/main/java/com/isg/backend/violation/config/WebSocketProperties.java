package com.isg.backend.violation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConfigurationProperties(prefix = "application.security.cors")
public class WebSocketProperties {

    private List<String> allowedOrigins;

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(
            List<String> allowedOrigins
    ) {
        if (allowedOrigins == null) {
            this.allowedOrigins = null;
            return;
        }

        this.allowedOrigins = allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(String::trim)
                .toList();
    }
}
