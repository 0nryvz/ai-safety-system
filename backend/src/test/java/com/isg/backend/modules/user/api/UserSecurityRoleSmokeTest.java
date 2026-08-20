package com.isg.backend.modules.user.api;

import com.isg.backend.modules.auth.infrastructure.InternalApiKeyFilter;
import com.isg.backend.modules.auth.infrastructure.JwtAuthenticationFilter;
import com.isg.backend.modules.auth.infrastructure.SecurityConfig;
import com.isg.backend.modules.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserSecurityRoleSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

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
    void anonymousCannotAccessMe() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users/me")
                )
                .andExpect(status().isUnauthorized());

        verify(userService, never())
                .getMe(any());
    }

    @Test
    void authenticatedUserCanAccessMe() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users/me")
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isOk());

        verify(userService)
                .getMe("shift@test.local");
    }

    @Test
    void ohsSpecialistCanAccessOwnDepartments() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users/me/departments")
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                )
                .andExpect(status().isOk());

        verify(userService)
                .getMyDepartments("ohs@test.local");
    }

    @Test
    void shiftSupervisorCanAccessOwnDepartments() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users/me/departments")
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isOk());

        verify(userService)
                .getMyDepartments("shift@test.local");
    }

    @Test
    void ohsSpecialistCannotListUsers() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users")
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                )
                .andExpect(status().isForbidden());

        verify(userService, never())
                .getAllUsers();
    }

    @Test
    void shiftSupervisorCannotListUsers() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users")
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isForbidden());

        verify(userService, never())
                .getAllUsers();
    }

    @Test
    void adminCanListUsers() throws Exception {
        mockMvc.perform(
                        get("/api/v1/users")
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(status().isOk());

        verify(userService)
                .getAllUsers();
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

            chain.doFilter(
                    request,
                    response
            );

            return null;
        })
                .when(filter)
                .doFilter(
                        any(),
                        any(),
                        any()
                );
    }
}