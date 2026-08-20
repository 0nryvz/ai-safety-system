package com.isg.backend.modules.camera.api.controller;

import com.isg.backend.modules.auth.infrastructure.InternalApiKeyFilter;
import com.isg.backend.modules.auth.infrastructure.JwtAuthenticationFilter;
import com.isg.backend.modules.auth.infrastructure.SecurityConfig;
import com.isg.backend.modules.camera.api.dto.CameraUpdateRequest;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.modules.camera.application.RestrictedZoneService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CameraController.class)
@Import(SecurityConfig.class)
class CameraSecurityRoleSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CameraService cameraService;

    @MockitoBean
    private RestrictedZoneService restrictedZoneService;

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
    void adminCanListCameras() throws Exception {
        mockMvc.perform(
                        get("/api/v1/cameras")
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(status().isOk());

        verify(cameraService)
                .getAllCameras();
    }

    @Test
    void ohsSpecialistCanListCameras() throws Exception {
        mockMvc.perform(
                        get("/api/v1/cameras")
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                )
                .andExpect(status().isOk());

        verify(cameraService)
                .getAllCameras();
    }

    @Test
    void shiftSupervisorCanListCameras() throws Exception {
        mockMvc.perform(
                        get("/api/v1/cameras")
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isOk());

        verify(cameraService)
                .getAllCameras();
    }

    @Test
    void anonymousCannotListCameras() throws Exception {
        mockMvc.perform(
                        get("/api/v1/cameras")
                )
                .andExpect(status().isUnauthorized());

        verify(cameraService, never())
                .getAllCameras();
    }

    @Test
    void adminCanGetCameraDetail() throws Exception {
        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/cameras/{id}",
                                cameraId
                        )
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(status().isOk());

        verify(cameraService)
                .getCameraById(cameraId);
    }

    @Test
    void ohsSpecialistCanGetCameraDetail() throws Exception {
        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/cameras/{id}",
                                cameraId
                        )
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                )
                .andExpect(status().isOk());

        verify(cameraService)
                .getCameraById(cameraId);
    }

    @Test
    void shiftSupervisorCanGetCameraDetail() throws Exception {
        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/cameras/{id}",
                                cameraId
                        )
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isOk());

        verify(cameraService)
                .getCameraById(cameraId);
    }

    @Test
    void adminCanUpdateCamera() throws Exception {
        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        put(
                                "/api/v1/cameras/{id}",
                                cameraId
                        )
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Updated Camera"
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        verify(cameraService)
                .updateCamera(
                        eq(cameraId),
                        any(CameraUpdateRequest.class)
                );
    }

    @Test
    void ohsSpecialistCannotUpdateCamera() throws Exception {
        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        put(
                                "/api/v1/cameras/{id}",
                                cameraId
                        )
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Updated Camera"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verify(cameraService, never())
                .updateCamera(
                        any(UUID.class),
                        any(CameraUpdateRequest.class)
                );
    }

    @Test
    void shiftSupervisorCannotUpdateCamera() throws Exception {
        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        put(
                                "/api/v1/cameras/{id}",
                                cameraId
                        )
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Updated Camera"
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verify(cameraService, never())
                .updateCamera(
                        any(UUID.class),
                        any(CameraUpdateRequest.class)
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