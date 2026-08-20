package com.isg.backend.modules.user.api;

import com.isg.backend.modules.auth.infrastructure.InternalApiKeyFilter;
import com.isg.backend.modules.auth.infrastructure.JwtAuthenticationFilter;
import com.isg.backend.modules.auth.infrastructure.SecurityConfig;
import com.isg.backend.modules.user.dto.UpdateUserRequest;
import com.isg.backend.modules.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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

    @Test
    void adminPatchPassesAuthenticatedEmailToService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        patch("/api/v1/users/{id}", userId)
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fullName": "Updated User"
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        verify(userService)
                .updateUser(
                        eq(userId),
                        any(UpdateUserRequest.class),
                        eq("admin@test.local")
                );
    }

    @Test
    void adminDeletePassesAuthenticatedEmailToService() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        delete("/api/v1/users/{id}", userId)
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(status().isNoContent());

        verify(userService)
                .deactivateUser(
                        userId,
                        "admin@test.local"
                );
    }

    @Test
    void anonymousCannotPatchUser() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        patch("/api/v1/users/{id}", userId)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fullName": "Updated User"
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());

        verify(userService, never())
                .updateUser(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void anonymousCannotDeleteUser() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        delete("/api/v1/users/{id}", userId)
                )
                .andExpect(status().isUnauthorized());

        verify(userService, never())
                .deactivateUser(
                        any(),
                        any()
                );
    }

    @Test
    void ohsSpecialistCannotPatchUser() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        patch("/api/v1/users/{id}", userId)
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fullName": "Updated User"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verify(userService, never())
                .updateUser(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void shiftSupervisorCannotPatchUser() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        patch("/api/v1/users/{id}", userId)
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "fullName": "Updated User"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verify(userService, never())
                .updateUser(
                        any(),
                        any(),
                        any()
                );
    }

    @Test
    void ohsSpecialistCannotDeleteUser() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        delete("/api/v1/users/{id}", userId)
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                )
                .andExpect(status().isForbidden());

        verify(userService, never())
                .deactivateUser(
                        any(),
                        any()
                );
    }

    @Test
    void shiftSupervisorCannotDeleteUser() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        delete("/api/v1/users/{id}", userId)
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isForbidden());

        verify(userService, never())
                .deactivateUser(
                        any(),
                        any()
                );
    }
    @Test
    void selfDeactivateDeleteReturnsControlledBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        doThrow(
                new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Kullanıcı kendi hesabını pasife alamaz."
                )
        )
                .when(userService)
                .deactivateUser(
                        userId,
                        "admin@test.local"
                );

        mockMvc.perform(
                        delete("/api/v1/users/{id}", userId)
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Kullanıcı kendi hesabını pasife alamaz."))
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/users/" + userId)
                );

        verify(userService)
                .deactivateUser(
                        userId,
                        "admin@test.local"
                );
    }

    @Test
    void selfDeactivatePatchReturnsControlledBadRequest() throws Exception {
        UUID userId = UUID.randomUUID();

        when(
                userService.updateUser(
                        eq(userId),
                        any(UpdateUserRequest.class),
                        eq("admin@test.local")
                )
        )
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.BAD_REQUEST,
                                "Kullanıcı kendi hesabını pasife alamaz."
                        )
                );

        mockMvc.perform(
                        patch("/api/v1/users/{id}", userId)
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "active": false
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message").value("Kullanıcı kendi hesabını pasife alamaz."))
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/users/" + userId)
                );

        verify(userService)
                .updateUser(
                        eq(userId),
                        any(UpdateUserRequest.class),
                        eq("admin@test.local")
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