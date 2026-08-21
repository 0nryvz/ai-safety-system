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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SecurityConfigCorsHttpTest.TestController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties =
        "APP_CORS_ALLOWED_ORIGINS=http://localhost:3000,https://demo.example.internal"
)
class SecurityConfigCorsHttpTest {

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
                                        "https://not-configured.example"
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