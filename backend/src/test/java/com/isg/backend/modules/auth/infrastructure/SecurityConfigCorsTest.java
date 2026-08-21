package com.isg.backend.modules.auth.infrastructure;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SecurityConfigCorsTest {

    private SecurityConfig securityConfig;

    @BeforeEach
    void setUp() {
        securityConfig = new SecurityConfig(
                mock(UserDetailsService.class),
                mock(PasswordEncoder.class)
        );
    }

    @Test
    void configuredNonLocalOriginIsAllowed() {
        ReflectionTestUtils.setField(
                securityConfig,
                "corsAllowedOrigins",
                List.of(
                        "http://localhost:3000",
                        "https://demo.example.internal"
                )
        );

        CorsConfiguration configuration =
                corsConfiguration();

        assertThat(
                configuration.checkOrigin(
                        "https://demo.example.internal"
                )
        )
                .isEqualTo(
                        "https://demo.example.internal"
                );
    }

    @Test
    void unconfiguredOriginIsRejected() {
        ReflectionTestUtils.setField(
                securityConfig,
                "corsAllowedOrigins",
                List.of(
                        "http://localhost:3000",
                        "http://localhost:5173"
                )
        );

        CorsConfiguration configuration =
                corsConfiguration();

        assertThat(
                configuration.checkOrigin(
                        "https://not-configured.example"
                )
        )
                .isNull();
    }

    @Test
    void localDevelopmentOriginsRemainAllowed() {
        ReflectionTestUtils.setField(
                securityConfig,
                "corsAllowedOrigins",
                List.of(
                        "http://localhost:3000",
                        "http://localhost:5173"
                )
        );

        CorsConfiguration configuration =
                corsConfiguration();

        assertThat(
                configuration.checkOrigin(
                        "http://localhost:3000"
                )
        )
                .isEqualTo(
                        "http://localhost:3000"
                );

        assertThat(
                configuration.checkOrigin(
                        "http://localhost:5173"
                )
        )
                .isEqualTo(
                        "http://localhost:5173"
                );
    }

    @Test
    void corsAllowsOptionsPreflight() {
        ReflectionTestUtils.setField(
                securityConfig,
                "corsAllowedOrigins",
                List.of(
                        "http://localhost:3000"
                )
        );

        CorsConfiguration configuration =
                corsConfiguration();

        assertThat(configuration.getAllowedMethods())
                .contains("OPTIONS");
    }

    @Test
    void credentialsAreEnabledWithoutWildcardOrigin() {
        ReflectionTestUtils.setField(
                securityConfig,
                "corsAllowedOrigins",
                List.of(
                        "http://localhost:3000",
                        "https://demo.example.internal"
                )
        );

        CorsConfiguration configuration =
                corsConfiguration();

        assertThat(configuration.getAllowCredentials())
                .isTrue();

        assertThat(configuration.getAllowedOrigins())
                .doesNotContain("*");
    }

    private CorsConfiguration corsConfiguration() {
        CorsConfigurationSource source =
                securityConfig.corsConfigurationSource();

        MockHttpServletRequest request =
                new MockHttpServletRequest(
                        "OPTIONS",
                        "/api/v1/cameras"
                );

        CorsConfiguration configuration =
                source.getCorsConfiguration(request);

        assertThat(configuration)
                .isNotNull();

        return configuration;
    }
}