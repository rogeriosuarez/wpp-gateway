package com.heureca.wppgateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

        @Bean
        public OpenAPI wppGatewayOpenAPI() {

                final String securitySchemeName = "ApiKeyAuth";

                return new OpenAPI()
                                .info(new Info()
                                                .title("WPP Gateway API")
                                                .description("""
                                                                WhatsApp Gateway API built on top of WPPConnect.

                                                                • Session management
                                                                • Messaging (text, media, interactive)
                                                                • Rate limiting per client and per session

                                                                Authentication is done using X-Api-Key header.
                                                                """)
                                                .version("v1"))
                                .addServersItem(new Server().url("https://api.seudominio.com"))
                                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                                .components(new io.swagger.v3.oas.models.Components()
                                                .addSecuritySchemes(securitySchemeName,
                                                                new SecurityScheme()
                                                                                .name("X-Api-Key")
                                                                                .type(SecurityScheme.Type.APIKEY)
                                                                                .in(SecurityScheme.In.HEADER)
                                                                                .description("Client API Key")));
        }
}
