package com.isg.backend.modules.auth.infrastructure; // Kendi paket adınla değiştir

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "application.security.internal")
public class InternalSecurityProperties {
    private String apiKey;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
}