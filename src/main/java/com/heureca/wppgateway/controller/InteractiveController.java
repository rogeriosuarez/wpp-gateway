package com.heureca.wppgateway.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.SessionUsageService;
import com.heureca.wppgateway.service.UsageService;
import com.heureca.wppgateway.service.WppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.tags.*;
import io.swagger.v3.oas.annotations.parameters.*;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/interactive")
@Tag(name = "Interactive", description = "Send interactive WhatsApp messages (passthrough to WPPConnect)")
@SecurityRequirement(name = "ApiKeyAuth")
public class InteractiveController {

    private final UsageService usageService;
    private final SessionUsageService sessionUsageService;
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public InteractiveController(
            UsageService usageService,
            SessionUsageService sessionUsageService,
            SessionRepository sessionRepository,
            WppService wppService) {
        this.usageService = usageService;
        this.sessionUsageService = sessionUsageService;
        this.sessionRepository = sessionRepository;
        this.wppService = wppService;
    }

    // =========================================================
    // SEND LIST
    // =========================================================

    @Operation(summary = "Send interactive list message", description = """
            Sends a interactive list message using an existing WhatsApp session.

            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or missing session", content = @Content(schema = @Schema(example = """
                    {
                      "error": "missing session in request body"
                    }
                    """))),
            @ApiResponse(responseCode = "401", description = "Invalid or missing API Key"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "409", description = "Session not connected"),
            @ApiResponse(responseCode = "429", description = "Daily usage limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Provider error")
    })
    @PostMapping("/send-list")
    public ResponseEntity<?> sendList(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples = @ExampleObject(name = "send-list-message", summary = "Interactive list example", value = """
                    {
                      "session": "my-session",
                      "phone": "552199999999",
                      "isGroup": false,
                      "description": "Desc for list",
                      "buttonText": "Select a option",
                      "sections": [
                        {
                          "title": "Section 1",
                          "rows": [
                            {
                              "rowId": "1",
                              "title": "Test 1",
                              "description": "Description 1"
                            },
                            {
                              "rowId": "2",
                              "title": "Test 2",
                              "description": "Description 2"
                            }
                          ]
                        }
                      ]
                    }
                    """))) @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        return proxy(body, request, ProxyType.LIST);
    }

    // =========================================================
    // SEND POLL
    // =========================================================

    @Operation(summary = "Send poll message", description = """
            Sends a poll message using an existing WhatsApp session.

            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Poll sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "401", description = "Invalid API Key"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "409", description = "Session not connected"),
            @ApiResponse(responseCode = "429", description = "Daily usage limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Provider error")
    })
    @PostMapping("/send-poll")
    public ResponseEntity<?> sendPoll(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples = @ExampleObject(name = "send-poll-message", summary = "Poll example", value = """
                    {
                      "session": "my-session",
                      "phone": "552199999999",
                      "isGroup": false,
                      "name": "Poll name",
                      "choices": [
                        "Option 1",
                        "Option 2",
                        "Option 3"
                      ],
                      "options": {
                        "selectableCount": 1
                      }
                    }
                    """))) @RequestBody Map<String, Object> body,
            HttpServletRequest request) {

        return proxy(body, request, ProxyType.POLL);
    }

    // =========================================================
    // CORE PROXY
    // =========================================================

    private ResponseEntity<?> proxy(
            Map<String, Object> body,
            HttpServletRequest request,
            ProxyType type) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        Object sessionObj = body.get("session");
        if (sessionObj == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "missing session in request body"));
        }

        String sessionName = sessionObj.toString();

        ResponseEntity<?> validation = validateSession(sessionName, client);
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");

        // 🔥 remove session before forwarding
        body.remove("session");

        ResponseEntity<?> response = switch (type) {
            case LIST -> wppService.sendListMessage(sessionName, token, body);
            case POLL -> wppService.sendPollMessage(sessionName, token, body);
        };

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(sessionName);

        return ResponseEntity.ok(response);
    }

    // =========================================================
    // VALIDATION
    // =========================================================

    private ResponseEntity<?> validateSession(String sessionName, ApiClient client) {

        var sessionOpt = sessionRepository.findBySessionName(sessionName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "session not found",
                    "session", sessionName));
        }

        SessionEntity session = sessionOpt.get();

        if (!session.getClientApiKey().equals(client.getApiKey())) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "session does not belong to client",
                    "session", sessionName));
        }

        if (session.getWppToken() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "wpp token missing",
                    "session", sessionName));
        }

        return ResponseEntity.ok(Map.of(
                "token", session.getWppToken()));
    }

    // =========================================================
    // INTERNAL
    // =========================================================

    private enum ProxyType {
        LIST,
        POLL
    }
}
