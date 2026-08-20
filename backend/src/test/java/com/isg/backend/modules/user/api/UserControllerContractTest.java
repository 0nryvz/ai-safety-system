package com.isg.backend.modules.user.api;

import com.isg.backend.modules.auth.infrastructure.InternalApiKeyFilter;
import com.isg.backend.modules.auth.infrastructure.JwtAuthenticationFilter;
import com.isg.backend.modules.auth.infrastructure.SecurityConfig;
import com.isg.backend.modules.user.dto.CreateUserRequest;
import com.isg.backend.modules.user.dto.UserResponse;
import com.isg.backend.modules.user.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
class UserControllerContractTest {

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
    void adminCanCreateUserAndReceivesCreatedResponse() throws Exception {
        UUID userId = UUID.randomUUID();

        UserResponse response = UserResponse.builder()
                .id(userId)
                .email("new.user@test.local")
                .fullName("New User")
                .active(true)
                .roles(Set.of("SHIFT_SUPERVISOR"))
                .build();

        when(userService.createUser(any(CreateUserRequest.class)))
                .thenReturn(response);

        mockMvc.perform(
                        post("/api/v1/users")
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "new.user@test.local",
                                          "password": "StrongPassword123!",
                                          "fullName": "New User",
                                          "roleNames": ["SHIFT_SUPERVISOR"]
                                        }
                                        """)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("new.user@test.local"))
                .andExpect(jsonPath("$.fullName").value("New User"))
                .andExpect(jsonPath("$.active").value(true));

        ArgumentCaptor<CreateUserRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateUserRequest.class);

        verify(userService)
                .createUser(requestCaptor.capture());

        assertThat(requestCaptor.getValue().getEmail())
                .isEqualTo("new.user@test.local");

        assertThat(requestCaptor.getValue().getRoleNames())
                .containsExactly("SHIFT_SUPERVISOR");
    }

    @Test
    void invalidCreateReturnsControlledValidationError() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "not-an-email",
                                          "password": "123",
                                          "fullName": "",
                                          "roleNames": []
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists())
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.roleNames").exists());

        verify(userService, never())
                .createUser(any(CreateUserRequest.class));
    }

    @Test
    void duplicateEmailReturnsControlledConflict() throws Exception {
        when(userService.createUser(any(CreateUserRequest.class)))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "Bu email adresi zaten kullanımda."
                        )
                );

        mockMvc.perform(
                        post("/api/v1/users")
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "existing@test.local",
                                          "password": "StrongPassword123!",
                                          "fullName": "Existing User",
                                          "roleNames": ["SHIFT_SUPERVISOR"]
                                        }
                                        """)
                )
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(
                        jsonPath("$.message")
                                .value("Bu email adresi zaten kullanımda.")
                )
                .andExpect(jsonPath("$.path").value("/api/v1/users"));
    }

    @Test
    void anonymousCannotCreateUser() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "new.user@test.local",
                                          "password": "StrongPassword123!",
                                          "fullName": "New User",
                                          "roleNames": ["SHIFT_SUPERVISOR"]
                                        }
                                        """)
                )
                .andExpect(status().isUnauthorized());

        verify(userService, never())
                .createUser(any(CreateUserRequest.class));
    }

    @Test
    void nonAdminCannotCreateUser() throws Exception {
        mockMvc.perform(
                        post("/api/v1/users")
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "email": "new.user@test.local",
                                          "password": "StrongPassword123!",
                                          "fullName": "New User",
                                          "roleNames": ["SHIFT_SUPERVISOR"]
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verify(userService, never())
                .createUser(any(CreateUserRequest.class));
    }

    @Test
    void adminCanGetUserById() throws Exception {
        UUID userId = UUID.randomUUID();

        UserResponse response = UserResponse.builder()
                .id(userId)
                .email("worker@test.local")
                .fullName("Worker User")
                .active(true)
                .roles(Set.of("SHIFT_SUPERVISOR"))
                .build();

        when(userService.getUserById(userId))
                .thenReturn(response);

        mockMvc.perform(
                        get("/api/v1/users/{id}", userId)
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(userId.toString()))
                .andExpect(jsonPath("$.email").value("worker@test.local"))
                .andExpect(jsonPath("$.fullName").value("Worker User"))
                .andExpect(jsonPath("$.active").value(true));

        verify(userService)
                .getUserById(userId);
    }

    @Test
    void unknownUserIdReturnsControlledNotFound() throws Exception {
        UUID userId = UUID.randomUUID();

        when(userService.getUserById(userId))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Kullanıcı bulunamadı."
                        )
                );

        mockMvc.perform(
                        get("/api/v1/users/{id}", userId)
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(
                        jsonPath("$.path")
                                .value("/api/v1/users/" + userId)
                );
    }

    @Test
    void anonymousCannotGetUserById() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        get("/api/v1/users/{id}", userId)
                )
                .andExpect(status().isUnauthorized());

        verify(userService, never())
                .getUserById(any());
    }

    @Test
    void nonAdminCannotGetUserById() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(
                        get("/api/v1/users/{id}", userId)
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isForbidden());

        verify(userService, never())
                .getUserById(any());
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
