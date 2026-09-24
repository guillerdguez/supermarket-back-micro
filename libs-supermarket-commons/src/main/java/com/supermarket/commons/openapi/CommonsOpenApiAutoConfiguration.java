package com.supermarket.commons.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

import java.util.List;

@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
public class CommonsOpenApiAutoConfiguration {

    public static final String SECURITY_SCHEME = "Bearer Authentication";

    @Bean
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI supermarketOpenApi(
            @Value("${spring.application.name:supermarket}") String applicationName,
            @Value("${supermarket.openapi.gateway-prefix:/api}") String gatewayPrefix) {
        return new OpenAPI()
                .info(new Info()
                        .title("Supermarket API - " + applicationName)
                        .version("1.0.0")
                        .description("RESTful API for Supermarket Management System (" + applicationName + ")")
                        .contact(new Contact().name("Supermarket Support").email("support@supermarket.com"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(new Server().url(gatewayPrefix).description("API Gateway")))
                .components(new Components().addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                        .name(SECURITY_SCHEME)
                        .description("JWT authentication with Bearer token")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .in(SecurityScheme.In.HEADER)));
    }
}
