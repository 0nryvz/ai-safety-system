package com.isg.backend.modules.auth.infrastructure;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String EMAIL =
            "be2-jwt@test.local";

    private static final String TOKEN =
            "valid-access-token";

    @Mock
    private JwtService jwtService;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private ObjectProvider<JwtService> jwtServiceProvider;

    @Mock
    private ObjectProvider<UserDetailsService> userDetailsServiceProvider;

    @Mock
    private FilterChain filterChain;

    private JwtAuthenticationFilter filter;

    private UserDetails activeUserDetails;
    private UserDetails inactiveUserDetails;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();

        filter =
                new JwtAuthenticationFilter(
                        jwtServiceProvider,
                        userDetailsServiceProvider
                );

        activeUserDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(EMAIL)
                        .password("unused")
                        .authorities(Collections.emptyList())
                        .disabled(false)
                        .build();

        inactiveUserDetails =
                org.springframework.security.core.userdetails.User
                        .withUsername(EMAIL)
                        .password("unused")
                        .authorities(Collections.emptyList())
                        .disabled(true)
                        .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void validTokenAuthenticatesActiveUser()
            throws Exception {

        stubProviders();

        MockHttpServletRequest request =
                bearerRequest(TOKEN);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(jwtService.extractUsername(TOKEN))
                .thenReturn(EMAIL);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(activeUserDetails);

        when(jwtService.isTokenValid(
                TOKEN,
                activeUserDetails
        ))
                .thenReturn(true);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        )
                .isNotNull();

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal()
        )
                .isSameAs(activeUserDetails);

        verify(jwtService)
                .isTokenValid(
                        TOKEN,
                        activeUserDetails
                );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void inactiveUserIsNotAuthenticatedEvenWithValidToken()
            throws Exception {

        stubProviders();

        MockHttpServletRequest request =
                bearerRequest(TOKEN);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(jwtService.extractUsername(TOKEN))
                .thenReturn(EMAIL);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(inactiveUserDetails);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        )
                .isNull();

        /*
         * Kullanıcı pasifse JWT teknik olarak geçerli olsa bile
         * validity kontrolüne geçilmemeli ve authentication
         * oluşturulmamalıdır.
         */
        verify(
                jwtService,
                never()
        )
                .isTokenValid(
                        TOKEN,
                        inactiveUserDetails
                );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void invalidTokenDoesNotAuthenticateUser()
            throws Exception {

        stubProviders();

        MockHttpServletRequest request =
                bearerRequest(TOKEN);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(jwtService.extractUsername(TOKEN))
                .thenReturn(EMAIL);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(activeUserDetails);

        when(jwtService.isTokenValid(
                TOKEN,
                activeUserDetails
        ))
                .thenReturn(false);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        )
                .isNull();

        verify(jwtService)
                .isTokenValid(
                        TOKEN,
                        activeUserDetails
                );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void requestWithoutBearerTokenPassesThrough()
            throws Exception {

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        )
                .isNull();

        verify(
                jwtServiceProvider,
                never()
        )
                .getIfAvailable();

        verify(
                userDetailsServiceProvider,
                never()
        )
                .getIfAvailable();

        verifyNoJwtInteractions();

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void malformedTokenDoesNotAuthenticateAndRequestContinues()
            throws Exception {

        stubProviders();

        MockHttpServletRequest request =
                bearerRequest(
                        "malformed-token"
                );

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(jwtService.extractUsername(
                "malformed-token"
        ))
                .thenThrow(
                        new IllegalArgumentException(
                                "Invalid JWT"
                        )
                );

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        )
                .isNull();

        verify(
                userDetailsService,
                never()
        )
                .loadUserByUsername(any());

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    @Test
    void existingAuthenticationIsNotOverwritten()
            throws Exception {

        stubProviders();

        MockHttpServletRequest request =
                bearerRequest(TOKEN);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        UsernamePasswordAuthenticationToken existing =
                new UsernamePasswordAuthenticationToken(
                        "existing-user",
                        null,
                        List.of()
                );

        SecurityContextHolder
                .getContext()
                .setAuthentication(existing);

        when(jwtService.extractUsername(TOKEN))
                .thenReturn(EMAIL);

        filter.doFilter(
                request,
                response,
                filterChain
        );

        assertThat(
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
        )
                .isSameAs(existing);

        verify(
                userDetailsService,
                never()
        )
                .loadUserByUsername(any());

        verify(
                jwtService,
                never()
        )
                .isTokenValid(
                        any(),
                        any()
                );

        verify(filterChain)
                .doFilter(
                        request,
                        response
                );
    }

    private void stubProviders() {
        when(jwtServiceProvider.getIfAvailable())
                .thenReturn(jwtService);

        when(userDetailsServiceProvider.getIfAvailable())
                .thenReturn(userDetailsService);
    }

    private void verifyNoJwtInteractions() {
        verify(
                jwtService,
                never()
        )
                .extractUsername(any());

        verify(
                userDetailsService,
                never()
        )
                .loadUserByUsername(any());
    }

    private MockHttpServletRequest bearerRequest(
            String token
    ) {
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.addHeader(
                "Authorization",
                "Bearer " + token
        );

        return request;
    }
}