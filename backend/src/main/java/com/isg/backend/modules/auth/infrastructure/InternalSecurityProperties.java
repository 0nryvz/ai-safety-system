package com.isg.backend.modules.auth.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "application.security.internal")
@Getter
@Setter
public class InternalSecurityProperties {
    private String apiKey;
}