package com.isg.backend.modules.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class DepartmentEntitySchemaContractTest {

    @Test
    void codeMappingMatchesDatabaseContract() throws Exception {
        Field codeField =
                Department.class.getDeclaredField("code");

        Column column =
                codeField.getAnnotation(Column.class);

        assertThat(column)
                .as("Department.code must be mapped with @Column")
                .isNotNull();

        assertThat(column.nullable())
                .as("departments.code is NOT NULL")
                .isFalse();

        assertThat(column.unique())
                .as("departments.code is UNIQUE")
                .isTrue();

        assertThat(column.length())
                .as("departments.code is varchar(40)")
                .isEqualTo(40);
    }

    @Test
    void nameMappingMatchesDatabaseContract() throws Exception {
        Field nameField =
                Department.class.getDeclaredField("name");

        Column column =
                nameField.getAnnotation(Column.class);

        assertThat(column)
                .as("Department.name must be mapped with @Column")
                .isNotNull();

        assertThat(column.nullable())
                .as("departments.name is NOT NULL")
                .isFalse();

        assertThat(column.unique())
                .as("departments.name is UNIQUE")
                .isTrue();

        assertThat(column.length())
                .as("departments.name is varchar(120)")
                .isEqualTo(120);
    }

    @Test
    void descriptionMappingMatchesDatabaseContract() throws Exception {
        Field descriptionField =
                Department.class.getDeclaredField("description");

        Column column =
                descriptionField.getAnnotation(Column.class);

        assertThat(column)
                .as("Department.description must be mapped with @Column")
                .isNotNull();

        assertThat(column.length())
                .as("departments.description is varchar(500)")
                .isEqualTo(500);
    }

    @Test
    void createdAtMappingMatchesDatabaseContract() throws Exception {
        Field createdAtField =
                Department.class.getDeclaredField("createdAt");

        Column column =
                createdAtField.getAnnotation(Column.class);

        assertThat(createdAtField.getType())
                .isEqualTo(OffsetDateTime.class);

        assertThat(createdAtField.getAnnotation(CreationTimestamp.class))
                .as("Department.createdAt must use the project CreationTimestamp standard")
                .isNotNull();

        assertThat(column)
                .isNotNull();

        assertThat(column.name())
                .isEqualTo("created_at");

        assertThat(column.nullable())
                .isFalse();

        assertThat(column.updatable())
                .isFalse();
    }

    @Test
    void updatedAtMappingMatchesDatabaseContract() throws Exception {
        Field updatedAtField =
                Department.class.getDeclaredField("updatedAt");

        Column column =
                updatedAtField.getAnnotation(Column.class);

        assertThat(updatedAtField.getType())
                .isEqualTo(OffsetDateTime.class);

        assertThat(updatedAtField.getAnnotation(UpdateTimestamp.class))
                .as("Department.updatedAt must use the project UpdateTimestamp standard")
                .isNotNull();

        assertThat(column)
                .isNotNull();

        assertThat(column.name())
                .isEqualTo("updated_at");

        assertThat(column.nullable())
                .isFalse();
    }

    @Test
    void versionMappingMatchesDatabaseContract() throws Exception {
        Field versionField =
                Department.class.getDeclaredField("version");

        Column column =
                versionField.getAnnotation(Column.class);

        assertThat(versionField.getType())
                .isEqualTo(Long.class);

        assertThat(versionField.getAnnotation(Version.class))
                .as("Department.version must use optimistic locking")
                .isNotNull();

        assertThat(column)
                .isNotNull();

        assertThat(column.nullable())
                .isFalse();
    }
}