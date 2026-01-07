package com.heureca.wppgateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class OpenApiConfig {

        public static final String SECURITY_SCHEME_NAME = "ApiKeyAuth";

        @Bean
        public OpenAPI wppGatewayOpenAPI() {

                return new OpenAPI()
                                .info(apiInfo())
                                .addServersItem(new Server()
                                                .url("https://wpp.heurecaai.com")
                                                .description("Production server"))
                                .addSecurityItem(
                                                new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                                .components(new Components()
                                                .addSecuritySchemes(
                                                                SECURITY_SCHEME_NAME,
                                                                apiKeyScheme()))
                                .addTagsItem(new Tag()
                                                .name("Sessions")
                                                .description("Create and manage WhatsApp sessions"))
                                .addTagsItem(new Tag()
                                                .name("Messages")
                                                .description("Send WhatsApp messages"))
                                .addTagsItem(new Tag()
                                                .name("Receive")
                                                .description("Receive WhatsApp messages (proxy from WPPConnect)"))
                                .addTagsItem(new Tag()
                                                .name("Interactive")
                                                .description("Send interactive WhatsApp messages (lists, buttons, polls)"))
                                .addTagsItem(new Tag()
                                                .name("Media")
                                                .description("Upload and manage media files"))
                                .addTagsItem(new Tag()
                                                .name("Account")
                                                .description("Usage, plans and limits"));
        }

        private Info apiInfo() {
                return new Info()
                                .title("WPP Gateway API")
                                .description("""
                                                🚀 **WPP Gateway** is a professional SaaS API for WhatsApp automation.

                                                ### 🔐 Authentication
                                                All requests require an API Key via HTTP header:
                                                ```
                                                X-Api-Key: YOUR_API_KEY
                                                ```

                                                ### 📦 Features
                                                - WhatsApp session management
                                                - Send text and interactive messages
                                                - Receive messages (transparent proxy)
                                                - Rate limiting per client and per session
                                                - Ready for RapidAPI monetization

                                                ### 🎯 Use cases
                                                - SaaS platforms
                                                - CRM systems
                                                - Chatbots
                                                - Notification services
                                                """)
                                .version("1.0.0")
                                .contact(new Contact()
                                                .name("HeurecaAI Support")
                                                .email("support@heurecaai.com")
                                                .url("https://heurecaai.com"));
        }

        private SecurityScheme apiKeyScheme() {
                return new SecurityScheme()
                                .name("X-Api-Key")
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.HEADER)
                                .description("API Key required to access the WPP Gateway API");
        }
}
