package com.isg.backend.shared.config;

import com.isg.backend.shared.web.ApiErrorResponse;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_AUTH = "bearerAuth";
    public static final String INTERNAL_API_KEY = "internalApiKey";
    public static final String INTERNAL_TAG = "Internal";

    @Bean
    public OpenAPI isgOpenApi() {
        Components components = new Components()
                .addSecuritySchemes(
                        BEARER_AUTH,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                )
                .addSecuritySchemes(
                        INTERNAL_API_KEY,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .name("X-Internal-Api-Key")
                );

        return new OpenAPI()
                .info(new Info()
                        .title("AI Destekli İSG Sistemi API")
                        .version("v1"))
                .components(components)
                .tags(List.of(
                        new Tag()
                                .name(INTERNAL_TAG)
                                .description(
                                        "Gateway ve AI Worker tarafından kullanılan internal endpointler"
                                )
                ));
    }

    @Bean
    public GroupedOpenApi internalApi() {
        return GroupedOpenApi.builder()
                .group("internal")
                .pathsToMatch("/internal/v1/**")
                .build();
    }

    @Bean
    public GlobalOpenApiCustomizer sharedOpenApiCustomizer() {
        return openApi -> {
            if (openApi.getComponents() == null) {
                openApi.setComponents(new Components());
            }

            Map<String, Schema> errorSchemas =
                    ModelConverters.getInstance().read(ApiErrorResponse.class);

            errorSchemas.forEach(openApi.getComponents()::addSchemas);

            Schema<?> apiErrorSchema =
                    openApi.getComponents().getSchemas().get("ApiErrorResponse");

            if (apiErrorSchema != null) {
                Map<String, Object> example = new LinkedHashMap<>();
                example.put("timestamp", "2026-08-17T19:00:00Z");
                example.put("status", 400);
                example.put("code", "VALIDATION_ERROR");
                example.put("message", "Validation failed");
                example.put("path", "/api/v1/example");
                example.put(
                        "correlationId",
                        "550e8400-e29b-41d4-a716-446655440000"
                );
                example.put(
                        "fieldErrors",
                        Map.of("field", "must not be blank")
                );

                apiErrorSchema.setExample(example);
            }

            if (openApi.getPaths() == null) {
                return;
            }

            openApi.getPaths().forEach((path, pathItem) -> {
                if (!path.startsWith("/internal/v1/")) {
                    return;
                }

                pathItem.readOperations().forEach(operation -> {
                    operation.addTagsItem(INTERNAL_TAG);
                    operation.addSecurityItem(
                            new SecurityRequirement().addList(INTERNAL_API_KEY)
                    );
                });
            });
        };
    }
}