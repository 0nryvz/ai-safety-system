package com.isg.backend.modules.camera.api.controller;

import com.isg.backend.modules.camera.api.dto.PointDto;
import com.isg.backend.modules.camera.domain.RestrictedZone;
import com.isg.backend.modules.camera.domain.RestrictedZoneRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class RestrictedZoneDbIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestrictedZoneRepository restrictedZoneRepository;

    @Autowired
    private EntityManager entityManager;

    private UUID departmentId;
    private UUID cameraId;
    private String adminEmail;

    @BeforeEach
    void setUp() {
        departmentId = UUID.randomUUID();
        cameraId = UUID.randomUUID();
        adminEmail =
                "rz-admin-"
                        + UUID.randomUUID()
                        + "@test.local";

        insertDepartment(
                departmentId,
                "RZ-" + shortId(),
                "Restricted Zone Department " + shortId()
        );

        insertCamera(
                cameraId,
                departmentId,
                "RZ-CAM-" + shortId(),
                "Restricted Zone Camera " + shortId()
        );

        UUID adminUserId =
                insertUser(
                        adminEmail,
                        "Restricted Zone Integration Admin"
                );

        assignRole(
                adminUserId,
                "ADMIN"
        );
    }

    @Test
    void adminPutThenGetPersistsNormalizedPolygonWithoutCoordinateDrift()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/cameras/{id}/restricted-zone",
                                cameraId
                        )
                                .with(
                                        user(adminEmail)
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "DB Round Trip Zone",
                                          "polygon": [
                                            {"x": 0.125, "y": 0.250},
                                            {"x": 0.875, "y": 0.250},
                                            {"x": 0.500, "y": 0.900}
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isOk());

        /*
         * PUT sırasında Hibernate'in tuttuğu managed entity'ye
         * güvenmemek için SQL'i DB'ye gerçekten gönder ve
         * persistence context'i temizle.
         */
        entityManager.flush();
        entityManager.clear();

        /*
         * Satırın gerçekten PostgreSQL restricted_zones
         * tablosuna yazıldığını doğrudan DB üzerinden doğrula.
         */
        assertThat(
                activeRestrictedZoneCount(cameraId)
        ).isEqualTo(1);

        /*
         * Temiz persistence context sonrasında repository
         * PostgreSQL JSONB kolonundan polygonu tekrar deserialize eder.
         */
        RestrictedZone persisted =
                restrictedZoneRepository
                        .findByCameraIdAndActiveTrue(cameraId)
                        .orElseThrow();

        assertThat(persisted.getName())
                .isEqualTo("DB Round Trip Zone");

        assertThat(persisted.getPolygon())
                .containsExactly(
                        new PointDto(0.125, 0.250),
                        new PointDto(0.875, 0.250),
                        new PointDto(0.500, 0.900)
                );

        /*
         * Repository read sonrasında oluşan managed entity'yi de
         * temizle. Böylece aşağıdaki GET yeniden DB read yoluna gider.
         */
        entityManager.clear();

        mockMvc.perform(
                        get(
                                "/api/v1/cameras/{id}/restricted-zone",
                                cameraId
                        )
                                .with(
                                        user(adminEmail)
                                                .roles("ADMIN")
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.name")
                                .value("DB Round Trip Zone")
                )
                .andExpect(
                        jsonPath("$.polygon.length()")
                                .value(3)
                )
                .andExpect(
                        jsonPath("$.polygon[0].x")
                                .value(0.125)
                )
                .andExpect(
                        jsonPath("$.polygon[0].y")
                                .value(0.250)
                )
                .andExpect(
                        jsonPath("$.polygon[1].x")
                                .value(0.875)
                )
                .andExpect(
                        jsonPath("$.polygon[1].y")
                                .value(0.250)
                )
                .andExpect(
                        jsonPath("$.polygon[2].x")
                                .value(0.500)
                )
                .andExpect(
                        jsonPath("$.polygon[2].y")
                                .value(0.900)
                );
    }

    @Test
    void putRejectsPolygonWithFewerThanThreePointsAndDoesNotPersist()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/cameras/{id}/restricted-zone",
                                cameraId
                        )
                                .with(
                                        user(adminEmail)
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Too Small Zone",
                                          "polygon": [
                                            {"x": 0.100, "y": 0.100},
                                            {"x": 0.900, "y": 0.900}
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        entityManager.flush();
        entityManager.clear();

        assertThat(
                activeRestrictedZoneCount(cameraId)
        ).isZero();
    }

    @Test
    void putRejectsOutOfRangeCoordinateAndDoesNotPersist()
            throws Exception {

        mockMvc.perform(
                        put(
                                "/api/v1/cameras/{id}/restricted-zone",
                                cameraId
                        )
                                .with(
                                        user(adminEmail)
                                                .roles("ADMIN")
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "name": "Out Of Range Zone",
                                          "polygon": [
                                            {"x": -0.010, "y": 0.100},
                                            {"x": 0.900, "y": 0.100},
                                            {"x": 0.900, "y": 1.010}
                                          ]
                                        }
                                        """)
                )
                .andExpect(status().isBadRequest());

        entityManager.flush();
        entityManager.clear();

        assertThat(
                activeRestrictedZoneCount(cameraId)
        ).isZero();
    }

    private int activeRestrictedZoneCount(
            UUID cameraId
    ) {
        Integer count =
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM restricted_zones
                        WHERE camera_id = ?
                          AND active = true
                        """,
                        Integer.class,
                        cameraId
                );

        return count == null
                ? 0
                : count;
    }

    private void insertDepartment(
            UUID id,
            String code,
            String name
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO departments (
                    id,
                    code,
                    name,
                    active
                )
                VALUES (?, ?, ?, true)
                """,
                id,
                code,
                name
        );
    }

    private void insertCamera(
            UUID id,
            UUID departmentId,
            String code,
            String name
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO cameras (
                    id,
                    code,
                    name,
                    department_id,
                    status,
                    active
                )
                VALUES (?, ?, ?, ?, 'OFFLINE', true)
                """,
                id,
                code,
                name,
                departmentId
        );
    }

    private UUID insertUser(
            String email,
            String fullName
    ) {
        UUID userId =
                UUID.randomUUID();

        jdbcTemplate.update(
                """
                INSERT INTO users (
                    id,
                    email,
                    password_hash,
                    full_name,
                    active
                )
                VALUES (?, ?, ?, ?, true)
                """,
                userId,
                email,
                "not-used-in-integration-test",
                fullName
        );

        return userId;
    }

    private void assignRole(
            UUID userId,
            String roleName
    ) {
        UUID roleId =
                jdbcTemplate.queryForObject(
                        """
                        SELECT id
                        FROM roles
                        WHERE name = ?
                        """,
                        UUID.class,
                        roleName
                );

        assertThat(roleId)
                .as(
                        "Required reference role '%s' must exist",
                        roleName
                )
                .isNotNull();

        jdbcTemplate.update(
                """
                INSERT INTO user_roles (
                    user_id,
                    role_id
                )
                VALUES (?, ?)
                """,
                userId,
                roleId
        );
    }

    private String shortId() {
        return UUID.randomUUID()
                .toString()
                .substring(0, 8);
    }
}