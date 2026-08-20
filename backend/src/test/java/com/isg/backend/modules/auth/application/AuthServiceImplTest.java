package com.isg.backend.modules.auth.application;

import com.isg.backend.modules.auth.api.dto.AuthResponse;
import com.isg.backend.modules.auth.api.dto.LoginRequest;
import com.isg.backend.modules.auth.entity.RefreshToken;
import com.isg.backend.modules.auth.infrastructure.JwtService;
import com.isg.backend.modules.auth.infrastructure.RefreshTokenRepository;
import com.isg.backend.modules.user.entity.User;
import com.isg.backend.modules.user.infrastructure.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    private static final String EMAIL =
            "be2-auth@test.local";

    private static final String PASSWORD =
            "StrongPassword123!";

    private static final Instant NOW =
            Instant.parse("2026-08-20T06:00:00Z");

    private static final long REFRESH_TOKEN_EXPIRATION_MILLIS =
            3_600_000L;

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private Clock clock;
    private AuthServiceImpl authService;

    private User activeUser;
    private User inactiveUser;

    @BeforeEach
    void setUp() {
        clock =
                Clock.fixed(
                        NOW,
                        ZoneOffset.UTC
                );

        authService =
                new AuthServiceImpl(
                        userRepository,
                        jwtService,
                        authenticationManager,
                        refreshTokenRepository,
                        clock
                );

        activeUser =
                User.builder()
                        .id(UUID.randomUUID())
                        .email(EMAIL)
                        .passwordHash("$2a$10$test")
                        .fullName("BE2 Active User")
                        .active(true)
                        .build();

        inactiveUser =
                User.builder()
                        .id(UUID.randomUUID())
                        .email(EMAIL)
                        .passwordHash("$2a$10$test")
                        .fullName("BE2 Inactive User")
                        .active(false)
                        .build();
    }

    @Test
    void successfulLoginAuthenticatesAndPersistsHashedRefreshToken() {
        ReflectionTestUtils.setField(
                authService,
                "refreshTokenExpiration",
                REFRESH_TOKEN_EXPIRATION_MILLIS
        );

        LoginRequest request =
                new LoginRequest(
                        EMAIL,
                        PASSWORD
                );

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(
                        Optional.of(activeUser)
                );

        when(authenticationManager.authenticate(
                any(Authentication.class)
        ))
                .thenReturn(
                        org.mockito.Mockito.mock(
                                Authentication.class
                        )
                );

        when(jwtService.generateToken(activeUser))
                .thenReturn("access-token");

        AuthResponse response =
                authService.login(request);

        assertThat(response.accessToken())
                .isEqualTo("access-token");

        assertThat(response.refreshToken())
                .isNotBlank();

        assertThat(response.tokenType())
                .isEqualTo("Bearer");

        verify(authenticationManager)
                .authenticate(
                        argThat(authentication ->
                                EMAIL.equals(
                                        authentication.getName()
                                )
                                        && PASSWORD.equals(
                                        authentication.getCredentials()
                                )
                        )
                );

        ArgumentCaptor<RefreshToken> tokenCaptor =
                ArgumentCaptor.forClass(
                        RefreshToken.class
                );

        verify(refreshTokenRepository)
                .save(
                        tokenCaptor.capture()
                );

        RefreshToken saved =
                tokenCaptor.getValue();

        assertThat(saved.getTokenHash())
                .isEqualTo(
                        hashToken(
                                response.refreshToken()
                        )
                );

        assertThat(saved.getTokenHash())
                .isNotEqualTo(
                        response.refreshToken()
                );

        assertThat(saved.getUser())
                .isSameAs(activeUser);

        assertThat(saved.isRevoked())
                .isFalse();

        assertThat(saved.getExpiresAt())
                .isEqualTo(
                        OffsetDateTime
                                .ofInstant(
                                        NOW,
                                        ZoneOffset.UTC
                                )
                                .plus(
                                        Duration.ofMillis(
                                                REFRESH_TOKEN_EXPIRATION_MILLIS
                                        )
                                )
                );
    }

    @Test
    void loginNormalizesEmailBeforeLookupAndAuthentication() {
        LoginRequest request =
                new LoginRequest(
                        "BE2-Auth@Test.Local",
                        PASSWORD
                );

        when(userRepository.findByEmail(
                org.mockito.ArgumentMatchers.anyString()
        ))
                .thenReturn(
                        Optional.of(activeUser)
                );

        when(authenticationManager.authenticate(
                any(Authentication.class)
        ))
                .thenReturn(
                        org.mockito.Mockito.mock(
                                Authentication.class
                        )
                );

        when(jwtService.generateToken(activeUser))
                .thenReturn("access-token");

        authService.login(request);

        verify(userRepository)
                .findByEmail(EMAIL);

        verify(authenticationManager)
                .authenticate(
                        argThat(authentication ->
                                EMAIL.equals(authentication.getName())
                                        && PASSWORD.equals(authentication.getCredentials())
                        )
                );
    }
    @Test
    void wrongPasswordIsRejectedWithoutIssuingTokens() {
        LoginRequest request =
                new LoginRequest(
                        EMAIL,
                        "wrong-password"
                );

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(
                        Optional.of(activeUser)
                );

        when(authenticationManager.authenticate(
                any(Authentication.class)
        ))
                .thenThrow(
                        new BadCredentialsException(
                                "Bad credentials"
                        )
                );

        assertThatThrownBy(
                () ->
                        authService.login(request)
        )
                .isInstanceOf(
                        BadCredentialsException.class
                );

        verifyNoInteractions(jwtService);

        verify(
                refreshTokenRepository,
                never()
        )
                .save(
                        any(RefreshToken.class)
                );
    }

    @Test
    void inactiveUserCannotLogin() {
        LoginRequest request =
                new LoginRequest(
                        EMAIL,
                        PASSWORD
                );

        when(userRepository.findByEmail(EMAIL))
                .thenReturn(
                        Optional.of(inactiveUser)
                );

        assertThatThrownBy(
                () ->
                        authService.login(request)
        )
                .isInstanceOf(
                        DisabledException.class
                );

        verifyNoInteractions(
                authenticationManager,
                jwtService,
                refreshTokenRepository
        );
    }

    @Test
    void validRefreshReturnsNewAccessTokenAndKeepsRefreshToken() {
        String plainRefreshToken =
                "valid-refresh-token";

        String tokenHash =
                hashToken(
                        plainRefreshToken
                );

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenHash(tokenHash)
                        .user(activeUser)
                        .expiresAt(
                                now()
                                        .plusHours(1)
                        )
                        .revoked(false)
                        .build();

        when(refreshTokenRepository.findByTokenHash(
                tokenHash
        ))
                .thenReturn(
                        Optional.of(refreshToken)
                );

        when(jwtService.generateToken(activeUser))
                .thenReturn(
                        "new-access-token"
                );

        AuthResponse response =
                authService.refreshToken(
                        plainRefreshToken
                );

        assertThat(response.accessToken())
                .isEqualTo(
                        "new-access-token"
                );

        assertThat(response.refreshToken())
                .isEqualTo(
                        plainRefreshToken
                );

        assertThat(response.tokenType())
                .isEqualTo(
                        "Bearer"
                );

        verify(jwtService)
                .generateToken(activeUser);
    }

    @Test
    void unknownRefreshTokenReturnsUnauthorized() {
        String plainRefreshToken =
                "unknown-refresh-token";

        String tokenHash =
                hashToken(
                        plainRefreshToken
                );

        when(refreshTokenRepository.findByTokenHash(
                tokenHash
        ))
                .thenReturn(
                        Optional.empty()
                );

        assertThatThrownBy(
                () ->
                        authService.refreshToken(
                                plainRefreshToken
                        )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.UNAUTHORIZED
                                        )
                );

        verifyNoInteractions(jwtService);
    }

    @Test
    void revokedRefreshTokenReturnsUnauthorized() {
        String plainRefreshToken =
                "revoked-refresh-token";

        String tokenHash =
                hashToken(
                        plainRefreshToken
                );

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenHash(tokenHash)
                        .user(activeUser)
                        .expiresAt(
                                now()
                                        .plusHours(1)
                        )
                        .revoked(true)
                        .build();

        when(refreshTokenRepository.findByTokenHash(
                tokenHash
        ))
                .thenReturn(
                        Optional.of(refreshToken)
                );

        assertThatThrownBy(
                () ->
                        authService.refreshToken(
                                plainRefreshToken
                        )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.UNAUTHORIZED
                                        )
                );

        verifyNoInteractions(jwtService);
    }

    @Test
    void expiredRefreshTokenReturnsUnauthorized() {
        String plainRefreshToken =
                "expired-refresh-token";

        String tokenHash =
                hashToken(
                        plainRefreshToken
                );

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenHash(tokenHash)
                        .user(activeUser)
                        .expiresAt(
                                now()
                                        .minusSeconds(1)
                        )
                        .revoked(false)
                        .build();

        when(refreshTokenRepository.findByTokenHash(
                tokenHash
        ))
                .thenReturn(
                        Optional.of(refreshToken)
                );

        assertThatThrownBy(
                () ->
                        authService.refreshToken(
                                plainRefreshToken
                        )
        )
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex ->
                                assertThat(
                                        ex.getStatusCode()
                                )
                                        .isEqualTo(
                                                HttpStatus.UNAUTHORIZED
                                        )
                );

        verifyNoInteractions(jwtService);
    }

    @Test
    void inactiveUserCannotRefreshAccessToken() {
        String plainRefreshToken =
                "inactive-user-refresh-token";

        String tokenHash =
                hashToken(
                        plainRefreshToken
                );

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenHash(tokenHash)
                        .user(inactiveUser)
                        .expiresAt(
                                now()
                                        .plusHours(1)
                        )
                        .revoked(false)
                        .build();

        when(refreshTokenRepository.findByTokenHash(
                tokenHash
        ))
                .thenReturn(
                        Optional.of(refreshToken)
                );

        assertThatThrownBy(
                () ->
                        authService.refreshToken(
                                plainRefreshToken
                        )
        )
                .isInstanceOf(
                        DisabledException.class
                );

        verifyNoInteractions(jwtService);
    }

    @Test
    void logoutRevokesExistingRefreshToken() {
        String plainRefreshToken =
                "logout-refresh-token";

        String tokenHash =
                hashToken(
                        plainRefreshToken
                );

        RefreshToken refreshToken =
                RefreshToken.builder()
                        .tokenHash(tokenHash)
                        .user(activeUser)
                        .expiresAt(
                                now()
                                        .plusHours(1)
                        )
                        .revoked(false)
                        .build();

        when(refreshTokenRepository.findByTokenHash(
                tokenHash
        ))
                .thenReturn(
                        Optional.of(refreshToken)
                );

        authService.logout(
                plainRefreshToken
        );

        assertThat(refreshToken.isRevoked())
                .isTrue();

        verify(refreshTokenRepository)
                .save(refreshToken);
    }

    @Test
    void blankLogoutDoesNotTouchRepository() {
        authService.logout("   ");

        verifyNoInteractions(
                refreshTokenRepository
        );

        verifyNoInteractions(
                jwtService
        );
    }

    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(
                NOW,
                ZoneOffset.UTC
        );
    }

    private String hashToken(
            String token
    ) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance(
                            "SHA-256"
                    );

            byte[] hash =
                    digest.digest(
                            token.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            return Base64
                    .getEncoder()
                    .encodeToString(hash);

        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(
                    "SHA-256 unavailable",
                    ex
            );
        }
    }
}