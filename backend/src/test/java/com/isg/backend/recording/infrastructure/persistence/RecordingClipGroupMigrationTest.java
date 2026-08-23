package com.isg.backend.recording.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RecordingClipGroupMigrationTest {

    @Container
    static final PostgreSQLContainer postgres =
            new PostgreSQLContainer("postgres:16-alpine")
                    .withDatabaseName("isg_shared_clip_test")
                    .withUsername("test")
                    .withPassword("test");

    @Test
    void flywayCreatesNullableClipGroupColumnAndIndex() throws Exception {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword()
                        )
                        .locations("classpath:db/migration")
                        .load();

        flyway.migrate();

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {

            try (ResultSet column = statement.executeQuery("""
                    SELECT is_nullable, data_type
                    FROM information_schema.columns
                    WHERE table_schema = 'public'
                      AND table_name = 'recordings'
                      AND column_name = 'clip_group_id'
                    """)) {

                assertThat(column.next()).isTrue();
                assertThat(column.getString("is_nullable"))
                        .isEqualTo("YES");
                assertThat(column.getString("data_type"))
                        .isEqualTo("uuid");
            }

            try (ResultSet index = statement.executeQuery("""
                    SELECT indexdef
                    FROM pg_indexes
                    WHERE schemaname = 'public'
                      AND tablename = 'recordings'
                      AND indexname = 'recordings_clip_group_idx'
                    """)) {

                assertThat(index.next()).isTrue();
                assertThat(index.getString("indexdef"))
                        .contains("clip_group_id")
                        .contains("WHERE (clip_group_id IS NOT NULL)");
            }
        }
    }
    @Test
    void databaseAllowsMultipleLogicalRecordingsToShareOneClip() throws Exception {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(
                                postgres.getJdbcUrl(),
                                postgres.getUsername(),
                                postgres.getPassword()
                        )
                        .locations("classpath:db/migration")
                        .load();

        flyway.migrate();

        try (Connection connection = postgres.createConnection("");
             Statement statement = connection.createStatement()) {

            statement.executeUpdate("""
                    INSERT INTO departments (id, code, name)
                    VALUES (
                        '10000000-0000-4000-8000-000000000001',
                        'CLIP-TEST',
                        'Shared Clip Test'
                    )
                    """);

            statement.executeUpdate("""
                    INSERT INTO cameras (
                        id,
                        code,
                        name,
                        department_id,
                        status
                    )
                    VALUES (
                        '20000000-0000-4000-8000-000000000001',
                        'CLIP-CAM-1',
                        'Shared Clip Camera',
                        '10000000-0000-4000-8000-000000000001',
                        'OFFLINE'
                    )
                    """);

            statement.executeUpdate("""
                    INSERT INTO violations (
                        id,
                        camera_id,
                        department_id,
                        violation_type,
                        started_at,
                        confidence,
                        model_version,
                        lifecycle_status,
                        review_status,
                        subject_key
                    )
                    VALUES
                    (
                        '30000000-0000-4000-8000-000000000001',
                        '20000000-0000-4000-8000-000000000001',
                        '10000000-0000-4000-8000-000000000001',
                        'MASK',
                        now(),
                        0.9000,
                        'test-model',
                        'ACTIVE',
                        'UNREVIEWED',
                        'track-42'
                    ),
                    (
                        '30000000-0000-4000-8000-000000000002',
                        '20000000-0000-4000-8000-000000000001',
                        '10000000-0000-4000-8000-000000000001',
                        'GLOVES',
                        now(),
                        0.9000,
                        'test-model',
                        'ACTIVE',
                        'UNREVIEWED',
                        'track-42'
                    ),
                    (
                        '30000000-0000-4000-8000-000000000003',
                        '20000000-0000-4000-8000-000000000001',
                        '10000000-0000-4000-8000-000000000001',
                        'JACKET',
                        now(),
                        0.9000,
                        'test-model',
                        'ACTIVE',
                        'UNREVIEWED',
                        'track-42'
                    ),
                    (
                        '30000000-0000-4000-8000-000000000004',
                        '20000000-0000-4000-8000-000000000001',
                        '10000000-0000-4000-8000-000000000001',
                        'MASK',
                        now(),
                        0.9000,
                        'test-model',
                        'PREPARING',
                        'UNREVIEWED',
                        'untracked'
                    )
                    """);

            statement.executeUpdate("""
                    INSERT INTO recordings (
                        id,
                        violation_id,
                        status,
                        start_command_id,
                        clip_group_id
                    )
                    VALUES
                    (
                        '40000000-0000-4000-8000-000000000001',
                        '30000000-0000-4000-8000-000000000001',
                        'RECORDING',
                        '50000000-0000-4000-8000-000000000001',
                        '60000000-0000-4000-8000-000000000001'
                    ),
                    (
                        '40000000-0000-4000-8000-000000000002',
                        '30000000-0000-4000-8000-000000000002',
                        'RECORDING',
                        NULL,
                        '60000000-0000-4000-8000-000000000001'
                    ),
                    (
                        '40000000-0000-4000-8000-000000000003',
                        '30000000-0000-4000-8000-000000000003',
                        'RECORDING',
                        NULL,
                        '60000000-0000-4000-8000-000000000001'
                    ),
                    (
                        '40000000-0000-4000-8000-000000000004',
                        '30000000-0000-4000-8000-000000000004',
                        'RECORDING',
                        '50000000-0000-4000-8000-000000000004',
                        NULL
                    )
                    """);

            try (ResultSet sharedRows = statement.executeQuery("""
                    SELECT
                        count(*) AS row_count,
                        count(start_command_id) AS physical_start_owner_count
                    FROM recordings
                    WHERE clip_group_id =
                        '60000000-0000-4000-8000-000000000001'
                    """)) {

                assertThat(sharedRows.next()).isTrue();
                assertThat(sharedRows.getInt("row_count"))
                        .isEqualTo(3);
                assertThat(sharedRows.getInt("physical_start_owner_count"))
                        .isEqualTo(1);
            }

            try (ResultSet legacyRow = statement.executeQuery("""
                    SELECT count(*) AS row_count
                    FROM recordings
                    WHERE violation_id =
                        '30000000-0000-4000-8000-000000000004'
                      AND clip_group_id IS NULL
                    """)) {

                assertThat(legacyRow.next()).isTrue();
                assertThat(legacyRow.getInt("row_count"))
                        .isEqualTo(1);
            }

            statement.executeUpdate("""
                    UPDATE recordings
                    SET status = 'READY',
                        object_key = 'recordings/shared/track-42.mp4',
                        duration_ms = 12000,
                        size_bytes = 4096,
                        checksum = 'shared-checksum',
                        ready_at = now()
                    WHERE clip_group_id =
                        '60000000-0000-4000-8000-000000000001'
                    """);

            try (ResultSet readyRows = statement.executeQuery("""
                    SELECT count(*) AS row_count
                    FROM recordings
                    WHERE clip_group_id =
                            '60000000-0000-4000-8000-000000000001'
                      AND status = 'READY'
                      AND object_key =
                            'recordings/shared/track-42.mp4'
                      AND duration_ms = 12000
                      AND size_bytes = 4096
                      AND checksum = 'shared-checksum'
                    """)) {

                assertThat(readyRows.next()).isTrue();
                assertThat(readyRows.getInt("row_count"))
                        .isEqualTo(3);
            }
        }
    }
}