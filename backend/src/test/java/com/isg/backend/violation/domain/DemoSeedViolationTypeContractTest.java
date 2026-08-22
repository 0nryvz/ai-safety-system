package com.isg.backend.violation.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class DemoSeedViolationTypeContractTest {

    private static final Pattern VIOLATION_INSERT_BLOCK =
            Pattern.compile(
                    "INSERT INTO violations\\b.*?ON CONFLICT",
                    Pattern.DOTALL
            );

    private static final Pattern VIOLATION_TYPE_LITERAL =
            Pattern.compile(
                    "'([A-Z][A-Z0-9_]*)',\\s*now\\(\\)"
            );

    @Test
    void demoSeedViolationTypesExistOnBusinessEnum()
            throws IOException {

        String seedSql =
                readDemoSeedSql();

        Matcher blockMatcher =
                VIOLATION_INSERT_BLOCK.matcher(
                        seedSql
                );

        assertThat(blockMatcher.find())
                .as("demo-seed.sql must contain an INSERT INTO violations block")
                .isTrue();

        String violationInsert =
                blockMatcher.group();

        Matcher typeMatcher =
                VIOLATION_TYPE_LITERAL.matcher(
                        violationInsert
                );

        List<String> seededTypes =
                new ArrayList<>();

        while (typeMatcher.find()) {
            seededTypes.add(
                    typeMatcher.group(1)
            );
        }

        assertThat(seededTypes)
                .as("demo-seed violations must declare at least one violation_type")
                .isNotEmpty();

        for (String seededType : seededTypes) {
            assertThatCode(
                    () -> ViolationType.valueOf(seededType)
            )
                    .as(
                            "demo-seed violation_type %s must exist on ViolationType",
                            seededType
                    )
                    .doesNotThrowAnyException();
        }
    }

    private static String readDemoSeedSql()
            throws IOException {

        try (InputStream inputStream =
                     DemoSeedViolationTypeContractTest.class
                             .getClassLoader()
                             .getResourceAsStream(
                                     "db/seed/demo-seed.sql"
                             )) {

            assertThat(inputStream)
                    .as("demo-seed.sql must be on the test classpath")
                    .isNotNull();

            return new String(
                    inputStream.readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
