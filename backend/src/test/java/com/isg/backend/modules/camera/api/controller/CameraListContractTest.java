package com.isg.backend.modules.camera.api.controller;

import com.isg.backend.modules.auth.infrastructure.InternalApiKeyFilter;
import com.isg.backend.modules.auth.infrastructure.JwtAuthenticationFilter;
import com.isg.backend.modules.auth.infrastructure.SecurityConfig;
import com.isg.backend.modules.camera.api.dto.CameraResponse;
import com.isg.backend.modules.camera.application.CameraService;
import com.isg.backend.modules.camera.application.ReferenceImageService;
import com.isg.backend.modules.camera.application.RestrictedZoneService;
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
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CameraController.class)
@Import(SecurityConfig.class)
class CameraListContractTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CameraService cameraService;

    @MockitoBean
    private RestrictedZoneService restrictedZoneService;

    @MockitoBean
    private ReferenceImageService referenceImageService;

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
    void listCamerasReturnsMobileMvpContract() throws Exception {
        UUID cameraId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();

        CameraResponse camera = CameraResponse.builder()
                .id(cameraId)
                .name("Production Camera")
                .code("CAM-001")
                .departmentId(departmentId)
                .departmentName("Production")
                .active(true)
                .status("ONLINE")
                .lastSeenAt(Instant.parse("2026-08-20T12:00:00Z"))
                .build();

        when(cameraService.getAllCameras())
                .thenReturn(List.of(camera));

        mockMvc.perform(
                        get("/api/v1/cameras")
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(cameraId.toString()))
                .andExpect(jsonPath("$[0].name").value("Production Camera"))
                .andExpect(jsonPath("$[0].code").value("CAM-001"))
                .andExpect(jsonPath("$[0].active").value(true))
                .andExpect(jsonPath("$[0].status").value("ONLINE"))
                .andExpect(jsonPath("$[0].departmentId").value(departmentId.toString()))
                .andExpect(jsonPath("$[0].departmentName").value("Production"));
    }

    private void makeFilterPassThrough(
            jakarta.servlet.Filter filter
    ) throws Exception {
        doAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);

            chain.doFilter(request, response);
            return null;
        })
                .when(filter)
                .doFilter(any(), any(), any());
    }
}