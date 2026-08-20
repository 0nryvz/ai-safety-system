package com.isg.backend.modules.camera.api.controller;

import com.isg.backend.modules.auth.infrastructure.InternalApiKeyFilter;
import com.isg.backend.modules.auth.infrastructure.JwtAuthenticationFilter;
import com.isg.backend.modules.auth.infrastructure.SecurityConfig;
import com.isg.backend.modules.camera.api.dto.CameraUpdateRequest;
import com.isg.backend.modules.camera.api.dto.ReferenceImageUrlResponse;
import com.isg.backend.modules.camera.api.dto.RestrictedZoneUpdateReq;
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
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

    @Test
    void adminCanUploadReferenceImage() throws Exception {
        UUID cameraId = UUID.randomUUID();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "reference.png",
                        MediaType.IMAGE_PNG_VALUE,
                        new byte[]{1, 2, 3}
                );

        mockMvc.perform(
                        multipart(
                                "/api/v1/cameras/{id}/reference-image",
                                cameraId
                        )
                                .file(file)
                                .with(
                                        user("admin@test.local")
                                                .roles("ADMIN")
                                )
                )
                .andExpect(status().isOk());

        verify(referenceImageService)
                .uploadReferenceImage(
                        eq(cameraId),
                        any(MultipartFile.class)
                );
    }

    @Test
    void ohsSpecialistCannotUploadReferenceImage() throws Exception {
        UUID cameraId = UUID.randomUUID();

        MockMultipartFile file =
                new MockMultipartFile(
                        "file",
                        "reference.png",
                        MediaType.IMAGE_PNG_VALUE,
                        new byte[]{1, 2, 3}
                );

        mockMvc.perform(
                        multipart(
                                "/api/v1/cameras/{id}/reference-image",
                                cameraId
                        )
                                .file(file)
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                )
                .andExpect(status().isForbidden());

        verify(referenceImageService, never())
                .uploadReferenceImage(
                        any(UUID.class),
                        any(MultipartFile.class)
                );
    }

    @Test
    void referenceImageUrlContractDoesNotExposeRawKey() throws Exception {
        UUID cameraId = UUID.randomUUID();

        Instant expiresAt =
                Instant.parse("2026-08-20T08:00:00Z");

        when(referenceImageService.getReferenceImageUrl(cameraId))
                .thenReturn(
                        new ReferenceImageUrlResponse(
                                "https://storage.test/reference-image?signature=test",
                                expiresAt
                        )
                );

        mockMvc.perform(
                        get(
                                "/api/v1/cameras/{id}/reference-image-url",
                                cameraId
                        )
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.url")
                                .value(
                                        "https://storage.test/reference-image?signature=test"
                                )
                )
                .andExpect(
                        jsonPath("$.expiresAt")
                                .value("2026-08-20T08:00:00Z")
                )
                .andExpect(
                        jsonPath("$.referenceImageKey")
                                .doesNotExist()
                );

        verify(referenceImageService)
                .getReferenceImageUrl(cameraId);
    }

    @Test
    void shiftSupervisorCanGetReferenceImageUrl() throws Exception {
        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/cameras/{id}/reference-image-url",
                                cameraId
                        )
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isOk());

        verify(referenceImageService)
                .getReferenceImageUrl(cameraId);
    }

    @Test
    void shiftSupervisorCanGetRestrictedZone() throws Exception {
        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        get(
                                "/api/v1/cameras/{id}/restricted-zone",
                                cameraId
                        )
                                .with(
                                        user("shift@test.local")
                                                .roles("SHIFT_SUPERVISOR")
                                )
                )
                .andExpect(status().isOk());

        verify(restrictedZoneService)
                .getRestrictedZoneDto(cameraId);
    }

    @Test
    void ohsSpecialistCannotUpdateRestrictedZone() throws Exception {
        UUID cameraId = UUID.randomUUID();

        mockMvc.perform(
                        put(
                                "/api/v1/cameras/{id}/restricted-zone",
                                cameraId
                        )
                                .with(
                                        user("ohs@test.local")
                                                .roles("OHS_SPECIALIST")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Test Zone",
                                          "polygon": [
                                            {"x": 0.10, "y": 0.10},
                                            {"x": 0.90, "y": 0.10},
                                            {"x": 0.90, "y": 0.90},
                                            {"x": 0.10, "y": 0.90}
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isForbidden());

        verify(restrictedZoneService, never())
                .updateRestrictedZone(
                        any(UUID.class),
                        any(RestrictedZoneUpdateReq.class)
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