package com.isg.backend.modules.user.api;

import com.isg.backend.modules.auth.infrastructure.InternalApiKeyFilter;
import com.isg.backend.modules.auth.infrastructure.JwtAuthenticationFilter;
import com.isg.backend.modules.auth.infrastructure.SecurityConfig;
import com.isg.backend.modules.user.dto.CreateDepartmentRequest;
import com.isg.backend.modules.user.dto.UpdateDepartmentRequest;
import com.isg.backend.modules.user.service.DepartmentService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DepartmentController.class)
@Import(SecurityConfig.class)
class DepartmentSecurityRoleSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DepartmentService departmentService;

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
    void authenticatedUserCanListDepartments() throws Exception {
        mockMvc.perform(
                        get("/api/v1/departments")
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isOk());

        verify(departmentService)
                .getAllDepartments();
    }

    @Test
    void anonymousCannotListDepartments() throws Exception {
        mockMvc.perform(
                        get("/api/v1/departments")
                )
                .andExpect(status().isUnauthorized());

        verify(departmentService, never())
                .getAllDepartments();
    }

    @Test
    void adminCanCreateDepartment() throws Exception {
        mockMvc.perform(
                        post("/api/v1/departments")
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "code": "KAYNAK-3",
                                          "name": "Yeni Kaynak Bölümü",
                                          "description": "Yeni üretim alanı"
                                        }
                                        """)
                )
                .andExpect(status().isCreated());

        verify(departmentService)
                .createDepartment(any(CreateDepartmentRequest.class));
    }

    @Test
    void nonAdminCannotCreateDepartment() throws Exception {
        mockMvc.perform(
                        post("/api/v1/departments")
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "code": "KAYNAK-3",
                                          "name": "Yeni Kaynak Bölümü"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verify(departmentService, never())
                .createDepartment(any(CreateDepartmentRequest.class));
    }

    @Test
    void adminCanUpdateDepartment() throws Exception {
        UUID departmentId = UUID.randomUUID();

        mockMvc.perform(
                        patch(
                                "/api/v1/departments/{id}",
                                departmentId
                        )
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Güncel Bölüm",
                                          "active": false
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        verify(departmentService)
                .updateDepartment(
                        eq(departmentId),
                        any(UpdateDepartmentRequest.class)
                );
    }

    @Test
    void nonAdminCannotUpdateDepartment() throws Exception {
        UUID departmentId = UUID.randomUUID();

        mockMvc.perform(
                        patch(
                                "/api/v1/departments/{id}",
                                departmentId
                        )
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "active": false
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verify(departmentService, never())
                .updateDepartment(
                        any(UUID.class),
                        any(UpdateDepartmentRequest.class)
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
}