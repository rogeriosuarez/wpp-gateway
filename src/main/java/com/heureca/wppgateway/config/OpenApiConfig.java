package com.heureca.wppgateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class OpenApiConfig {
        public static final String SECURITY_SCHEME_NAME = "ApiKeyAuth";
        @Value("${app.version:dev}")
        private String appVersion;

        @Bean
        public OpenAPI wppGatewayOpenAPI() {

                return new OpenAPI()
                                .info(apiInfo());
                                // .components(
                                //                 new Components()
                                //                                 .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                //                                                 internalApiKeyScheme())
                                //                                 // .addSecuritySchemes("RapidApiKey", rapidApiKeyScheme())
                                //                                 )
                                // // 🔐 Default security = INTERNAL
                                // .addSecurityItem(
                                //                 new SecurityRequirement().addList(SECURITY_SCHEME_NAME));
        }

        private Info apiInfo() {
                return new Info()
                                .title("WPP Gateway API")
                                .description("""
                                                🚀 **WPP Gateway** is a professional SaaS API for WhatsApp automation.

                                                ### 🔐 Authentication
                                                All requests require an API Key via HTTP header:
                                                ```
                                                 - API Key must be provided in header `X-Api-Key`
                                                 - RapidAPI and internal keys are supported
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
                                .version(appVersion)
                                .contact(new Contact()
                                                .name("HeurecaAI Support")
                                                .email("support@heurecaai.com")
                                                .url("https://heurecaai.com"));
        }

        // private SecurityScheme internalApiKeyScheme() {
        //         return new SecurityScheme()
        //                         .type(SecurityScheme.Type.APIKEY)
        //                         .in(SecurityScheme.In.HEADER)
        //                         .name("X-Api-Key")
        //                         .description("""
        //                                         🔐 Internal API Key authentication

        //                                         - Required for all internal and RapidAPI calls
        //                                         - Issued via /admin/create-client
        //                                         - MUST be sent as header: X-Api-Key

        //                                         ⚠️ When calling via RapidAPI, this key is STILL REQUIRED.
        //                                         """);
        // }

        // private SecurityScheme rapidApiKeyScheme() {
        //         return new SecurityScheme()
        //                         .type(SecurityScheme.Type.APIKEY)
        //                         .in(SecurityScheme.In.HEADER)
        //                         .name("X-RapidAPI-Proxy-Secret")
        //                         .description("""
        //                                         🔥 RapidAPI Gateway authentication

        //                                         - Automatically injected by RapidAPI
        //                                         - DO NOT send this header manually
        //                                         - Used to validate calls coming from RapidAPI infrastructure
        //                                         """);
        // }

}
