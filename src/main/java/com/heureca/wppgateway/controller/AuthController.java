package com.heureca.wppgateway.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.ClientSource;
import com.heureca.wppgateway.service.ApiClientService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/admin")
@Tag(name = "Admin", description = "Administrative and bootstrap endpoints")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class AuthController {

        private final ApiClientService apiClientService;

        public AuthController(ApiClientService clientService) {
                this.apiClientService = clientService;
        }

        // /**
        //  * Creates an ApiClient and returns an X-Api-Key.
        //  *
        //  * This endpoint is used to bootstrap authentication.
        //  * The returned apiKey must be used in all subsequent requests.
        //  *
        //  * 🔐 Authentication:
        //  * - No X-Api-Key required for this endpoint
        //  * - Client source (RAPID or INTERNAL) is inferred automatically
        //  */
        // @Operation(summary = "Create API client", description = """
        //                 Creates a new API client and returns an X-Api-Key.

        //                 This endpoint is used to bootstrap authentication.

        //                 The returned X-Api-Key must be sent in the `X-Api-Key` header
        //                 for all subsequent requests.
        //                 """)
        // @ApiResponses({
        //                 @ApiResponse(responseCode = "201", description = "Client created successfully", content = @Content(mediaType = "application/json", schema = @Schema(example = """
        //                                 {
        //                                   "name": "my-client",
        //                                   "X-Api-Key": "c155a442fe534c1f9960ea5b5d4a9b65",
        //                                   "type": "RAPID"
        //                                 }
        //                                 """))),
        //                 @ApiResponse(responseCode = "400", description = "Invalid request"),
        //                 @ApiResponse(responseCode = "500", description = "Internal error")
        // })
        @PostMapping("/create-client")
        public ResponseEntity<?> createClient(
                        HttpServletRequest request,

                        @Parameter(description = "Client name used for identification", required = true, example = "my-client") @RequestParam String name) {

                ClientSource clientSource = (ClientSource) request.getAttribute("clientSource");

                ApiClient client = apiClientService.createClient(
                                name,
                                null, // unlimited initially
                                clientSource);

                return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                                "name", client.getName(),
                                "X-Api-Key", client.getApiKey(),
                                "type", client.getSource()));
        }

}
