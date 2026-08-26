package com.isg.backend.modules.auth.infrastructure;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigCorsHttpTest.TestController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties =
        "APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,http://localhost:5173,http://192.168.137.10:5173,https://demo.example.internal"
)
class SecurityConfigCorsHttpTest {

    private static final String LOCAL_WEB_ORIGIN =
            "http://localhost:5173";

    private static final String LAN_WEB_ORIGIN =
            "http://192.168.137.10:5173";

    private static final String UNCONFIGURED_ORIGIN =
            "http://evil.example";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserDetailsService userDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private InternalApiKeyFilter internalApiKeyFilter;

    @MockitoBean
    private Clock clock;

    @BeforeEach
    void setUp() throws Exception {
        makeFilterPassThrough(jwtAuthenticationFilter);
        makeFilterPassThrough(internalApiKeyFilter);
    }

    @Test
    void localhostOriginPassesPreflight() throws Exception {
        mockMvc.perform(
                        options("/cors-test")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        LOCAL_WEB_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                LOCAL_WEB_ORIGIN
                        )
                );
    }

    @Test
    void configuredLanOriginPassesPreflight() throws Exception {
        mockMvc.perform(
                        options("/cors-test")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        LAN_WEB_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                LAN_WEB_ORIGIN
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                                "true"
                        )
                );
    }

    @Test
    void configuredNonLocalOriginPassesPreflight() throws Exception {
        mockMvc.perform(
                        options("/cors-test")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        "https://demo.example.internal"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                "https://demo.example.internal"
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                                "true"
                        )
                );
    }

    @Test
    void unconfiguredOriginIsRejectedDuringPreflight() throws Exception {
        mockMvc.perform(
                        options("/cors-test")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        UNCONFIGURED_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                )
                .andExpect(status().isForbidden())
                .andExpect(
                        header().doesNotExist(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN
                        )
                );
    }

    @Test
    void authorizationPreflightIsAllowedForConfiguredLanOrigin() throws Exception {
        mockMvc.perform(
                        options("/api/v1/cameras")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        LAN_WEB_ORIGIN
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD,
                                        "GET"
                                )
                                .header(
                                        HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                                        HttpHeaders.AUTHORIZATION
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                LAN_WEB_ORIGIN
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
                                "true"
                        )
                )
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                                HttpHeaders.AUTHORIZATION
                        )
                );
    }

    @Test
    void configuredLanOriginDoesNotBypassAuthentication() throws Exception {
        mockMvc.perform(
                        get("/api/v1/cameras")
                                .header(
                                        HttpHeaders.ORIGIN,
                                        LAN_WEB_ORIGIN
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        header().string(
                                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                                LAN_WEB_ORIGIN
                        )
                );
    }

    private void makeFilterPassThrough(
            jakarta.servlet.Filter filter
    ) throws Exception {

        doAnswer(invocation -> {
            ServletRequest request =
                    invocation.getArgument(0);

            ServletResponse response =
                    invocation.getArgument(1);

            FilterChain chain =
                    invocation.getArgument(2);

            chain.doFilter(request, response);

            return null;
        })
                .when(filter)
                .doFilter(
                        any(),
                        any(),
                        any()
                );
    }

    @RestController
    static class TestController {

        @GetMapping("/cors-test")
        String corsTest() {
            return "ok";
        }
    }
}