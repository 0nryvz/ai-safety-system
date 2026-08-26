package com.isg.backend.modules.auth.infrastructure;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String USERNAME = "admin@example.com";

    private static final String SECRET_KEY = Base64.getEncoder()
            .encodeToString(
                    "0123456789abcdef0123456789abcdef"
                            .getBytes(StandardCharsets.UTF_8)
            );

    private static final String DIFFERENT_SECRET_KEY = Base64.getEncoder()
            .encodeToString(
                    "abcdef0123456789abcdef0123456789"
                            .getBytes(StandardCharsets.UTF_8)
            );

    private JwtService jwtService;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        jwtService = createJwtService(
                SECRET_KEY,
                900_000L
        );

        userDetails = User.withUsername(USERNAME)
                .password("unused")
                .authorities("ROLE_ADMIN")
                .build();
    }

    @Test
    void generatedTokenContainsUserEmailAsSubject() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.extractUsername(token))
                .isEqualTo(USERNAME);
    }

    @Test
    void generatedTokenPreservesExtraClaims() {
        String token = jwtService.generateToken(
                Map.of("role", "ADMIN"),
                userDetails
        );

        String role = jwtService.extractClaim(
                token,
                claims -> claims.get("role", String.class)
        );

        assertThat(role)
                .isEqualTo("ADMIN");
    }

    @Test
    void tokenIsValidForMatchingUser() {
        String token = jwtService.generateToken(userDetails);

        assertThat(jwtService.isTokenValid(token, userDetails))
                .isTrue();
    }

    @Test
    void tokenIsInvalidForDifferentUser() {
        String token = jwtService.generateToken(userDetails);

        UserDetails differentUser = User.withUsername("other@example.com")
                .password("unused")
                .authorities("ROLE_ADMIN")
                .build();

        assertThat(jwtService.isTokenValid(token, differentUser))
                .isFalse();
    }

    @Test
    void expiredTokenIsRejected() {
        JwtService expiredTokenService = createJwtService(
                SECRET_KEY,
                -1_000L
        );

        String token = expiredTokenService.generateToken(userDetails);

        assertThatThrownBy(
                () -> expiredTokenService.isTokenValid(
                        token,
                        userDetails
                )
        )
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() {
        JwtService otherJwtService = createJwtService(
                DIFFERENT_SECRET_KEY,
                900_000L
        );

        String token = otherJwtService.generateToken(userDetails);

        assertThatThrownBy(
                () -> jwtService.isTokenValid(
                        token,
                        userDetails
                )
        )
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void validSecretPassesStartupValidation() {
        JwtService service = createJwtService(
                SECRET_KEY,
                900_000L
        );

        assertThatCode(service::validateConfiguration)
                .doesNotThrowAnyException();
    }

    @Test
    void invalidBase64SecretFailsStartupValidation() {
        JwtService service = createJwtService(
                "%%%not-base64%%%",
                900_000L
        );

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Base64");
    }

    @Test
    void tooShortSecretFailsStartupValidation() {
        String shortSecret = Base64.getEncoder()
                .encodeToString(
                        "too-short"
                                .getBytes(StandardCharsets.UTF_8)
                );

        JwtService service = createJwtService(
                shortSecret,
                900_000L
        );

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("32");
    }

    private JwtService createJwtService(
            String secretKey,
            long expiration
    ) {
        JwtService service = new JwtService();

        ReflectionTestUtils.setField(
                service,
                "secretKey",
                secretKey
        );

        ReflectionTestUtils.setField(
                service,
                "jwtExpiration",
                expiration
        );

        return service;
    }
}