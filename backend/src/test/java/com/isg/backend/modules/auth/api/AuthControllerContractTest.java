package com.isg.backend.modules.auth.api;

import com.isg.backend.modules.auth.api.dto.AuthResponse;
import com.isg.backend.modules.auth.api.dto.LoginRequest;
import com.isg.backend.modules.auth.application.AuthService;
import com.isg.backend.shared.web.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerContractTest {

    private static final Instant NOW =
            Instant.parse("2026-08-20T06:00:00Z");

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Clock clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        AuthController authController =
                new AuthController(
                        authService
                );

        GlobalExceptionHandler exceptionHandler =
                new GlobalExceptionHandler(
                        clock
                );

        mockMvc =
                MockMvcBuilders
                        .standaloneSetup(
                                authController
                        )
                        .setControllerAdvice(
                                exceptionHandler
                        )
                        .build();
    }

    @Test
    void successfulLoginReturnsTokenContract()
            throws Exception {

        when(authService.login(
                any(LoginRequest.class)
        ))
                .thenReturn(
                        new AuthResponse(
                                "access-token",
                                "refresh-token"
                        )
                );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": "user@test.local",
                                          "password": "StrongPassword123!"
                                        }
                                        """)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.accessToken")
                                .value("access-token")
                )
                .andExpect(
                        jsonPath("$.refreshToken")
                                .value("refresh-token")
                )
                .andExpect(
                        jsonPath("$.tokenType")
                                .value("Bearer")
                );

        verify(authService)
                .login(
                        any(LoginRequest.class)
                );
    }

    @Test
    void badCredentialsReturnControlledUnauthorized()
            throws Exception {

        when(authService.login(
                any(LoginRequest.class)
        ))
                .thenThrow(
                        new BadCredentialsException(
                                "Bad credentials"
                        )
                );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": "user@test.local",
                                          "password": "wrong-password"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED")
                );

        verify(authService)
                .login(
                        any(LoginRequest.class)
                );
    }

    @Test
    void disabledUserLoginReturnsControlledUnauthorized()
            throws Exception {

        when(authService.login(
                any(LoginRequest.class)
        ))
                .thenThrow(
                        new DisabledException(
                                "Hesap pasif durumdadır."
                        )
                );

        mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "email": "disabled@test.local",
                                          "password": "StrongPassword123!"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED")
                );

        verify(authService)
                .login(
                        any(LoginRequest.class)
                );
    }

    @Test
    void successfulRefreshReturnsTokenContract()
            throws Exception {

        when(authService.refreshToken(
                "valid-refresh-token"
        ))
                .thenReturn(
                        new AuthResponse(
                                "new-access-token",
                                "valid-refresh-token"
                        )
                );

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": "valid-refresh-token"
                                        }
                                        """)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.accessToken")
                                .value("new-access-token")
                )
                .andExpect(
                        jsonPath("$.refreshToken")
                                .value("valid-refresh-token")
                )
                .andExpect(
                        jsonPath("$.tokenType")
                                .value("Bearer")
                );

        verify(authService)
                .refreshToken(
                        "valid-refresh-token"
                );
    }

    @Test
    void blankRefreshTokenReturnsValidationError()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": "   "
                                        }
                                        """)
                )
                .andExpect(
                        status().isBadRequest()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(400)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("VALIDATION_ERROR")
                )
                .andExpect(
                        jsonPath("$.fieldErrors.refreshToken")
                                .exists()
                );

        verify(
                authService,
                never()
        )
                .refreshToken(
                        any()
                );
    }

    @Test
    void invalidRefreshTokenReturnsControlledUnauthorized()
            throws Exception {

        when(authService.refreshToken(
                "invalid-refresh-token"
        ))
                .thenThrow(
                        new ResponseStatusException(
                                org.springframework.http.HttpStatus.UNAUTHORIZED,
                                "Geçersiz Refresh Token"
                        )
                );

        mockMvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": "invalid-refresh-token"
                                        }
                                        """)
                )
                .andExpect(
                        status().isUnauthorized()
                )
                .andExpect(
                        jsonPath("$.status")
                                .value(401)
                )
                .andExpect(
                        jsonPath("$.code")
                                .value("UNAUTHORIZED")
                );
    }

    @Test
    void successfulLogoutReturnsOk()
            throws Exception {

        mockMvc.perform(
                        post("/api/v1/auth/logout")
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content("""
                                        {
                                          "refreshToken": "logout-refresh-token"
                                        }
                                        """)
                )
                .andExpect(
                        status().isOk()
                );

        verify(authService)
                .logout(
                        "logout-refresh-token"
                );
    }
}