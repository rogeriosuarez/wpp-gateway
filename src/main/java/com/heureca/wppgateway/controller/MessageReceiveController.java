package com.heureca.wppgateway.controller;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.SessionUsageService;
import com.heureca.wppgateway.service.UsageService;
import com.heureca.wppgateway.service.WppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/receive")
@Tag(name = "Message Receive", description = "Receive WhatsApp messages from an active session (proxy to WPPConnect). API key must be in header 'X-Api-Key'")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class MessageReceiveController {

    private static final Logger logger = LoggerFactory.getLogger(MessageReceiveController.class);

    private final SessionRepository sessionRepository;
    private final WppService wppService;
    private final UsageService usageService;
    private final SessionUsageService sessionUsageService;

    public MessageReceiveController(
            SessionRepository sessionRepository,
            WppService wppService,
            UsageService usageService,
            SessionUsageService sessionUsageService) {
        this.sessionRepository = sessionRepository;
        this.wppService = wppService;
        this.usageService = usageService;
        this.sessionUsageService = sessionUsageService;
    }

    @Operation(summary = "Get all unread WhatsApp messages", description = """
            Returns all unread messages from a WhatsApp session.

            ### Notes
            - Authentication and client validation are handled by the filter
            - This endpoint focuses on WhatsApp delivery rules
            - API key must be in header 'X-Api-Key'
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Unread messages retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Session not found or invalid request"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "409", description = "Session not connected"),
            @ApiResponse(responseCode = "429", description = "Session daily limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Failed to fetch messages from WPPConnect")
    })
    @GetMapping("/{session}/all-unread-messages")
    public ResponseEntity<?> getAllUnreadMessages(
            @Parameter(name = "X-Api-Key", description = "RapidAPI or internal API Key", required = true, in = ParameterIn.HEADER) @RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session,
            HttpServletRequest request) {

        return processMessageRequest(session, null, request);
    }

    @Operation(summary = "Get all messages from a specific chat", description = """
            Returns all messages exchanged with a specific phone number in a WhatsApp session.

            ### Notes
            - Authentication and client validation are handled by the filter
            - This endpoint focuses on WhatsApp delivery rules
            - API key must be in header 'X-Api-Key'
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Chat messages retrieved successfully"),
            @ApiResponse(responseCode = "400", description = "Session or phone not found"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "409", description = "Session not connected"),
            @ApiResponse(responseCode = "429", description = "Session daily limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Failed to fetch messages from WPPConnect")
    })
    @GetMapping("/{session}/all-messages-in-chat/{phone}")
    public ResponseEntity<?> getAllMessagesInChat(
            @Parameter(name = "X-Api-Key", description = "RapidAPI or internal API Key", required = true, in = ParameterIn.HEADER) @RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session,
            @PathVariable String phone,
            HttpServletRequest request) {

        return processMessageRequest(session, phone, request);
    }

    /*
     * ==========================
     * Internals
     * ==========================
     */
    private ResponseEntity<?> processMessageRequest(String sessionName, String phone, HttpServletRequest request) {

        // ApiClient já está validado pelo filter
        Object clientAttr = request.getAttribute("apiClient");
        if (clientAttr == null) {
            return ResponseEntity.status(401).body(Map.of("error", "client not found in request context"));
        }

        // 1. Validate session existence
        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(sessionName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found", "session", sessionName));
        }

        SessionEntity session = sessionOpt.get();

        // 2. Validate session status
        if (!"CONNECTED".equalsIgnoreCase(session.getStatus())) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "session not connected",
                    "status", session.getStatus()));
        }

        // 3. Session daily limit (anti-block)
        if (!sessionUsageService.canSendMessage(sessionName)) {
            int used = sessionUsageService.getUsageToday(sessionName);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "session daily limit exceeded (anti-block protection)",
                    "limit", 450,
                    "used", used,
                    "session", sessionName));
        }

        // 4. Validate WPP token
        String token = session.getWppToken();
        if (token == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "wpp token missing for session",
                    "session", sessionName));
        }

        try {
            Map<?, ?> wppResponse;
            if (phone != null) {
                wppResponse = wppService.getAllMessagesInChat(sessionName, token, phone);
            } else {
                wppResponse = wppService.getAllUnreadMessages(sessionName, token);
            }

            logger.info("MESSAGES RECEIVED | session={} | phone={}", sessionName, phone);

            sessionUsageService.recordUsage(sessionName);

            return ResponseEntity.ok(wppResponse);
        } catch (Exception e) {
            logger.error("ERROR RECEIVING MESSAGES | session={}", sessionName, e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to fetch messages from WPPConnect",
                    "message", e.getMessage(),
                    "session", sessionName,
                    "phone", phone));
        }
    }
}
