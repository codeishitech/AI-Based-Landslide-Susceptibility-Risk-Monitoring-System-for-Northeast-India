package com.ner.landslide.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Exposes interactive API docs at /swagger-ui.html backed by /v3/api-docs. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI landslideOpenApi() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("NER Landslide Early-Warning API")
                        .description("AI-powered real-time landslide risk monitoring and early-warning platform "
                                + "for the North Eastern Region of India.")
                        .version("v1")
                        .contact(new Contact().name("NER Landslide Platform Team")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
