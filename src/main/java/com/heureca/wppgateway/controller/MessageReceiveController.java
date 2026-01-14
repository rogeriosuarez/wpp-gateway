package com.heureca.wppgateway.controller;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.SessionUsageService;
import com.heureca.wppgateway.service.UsageService;
import com.heureca.wppgateway.service.WppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/receive")
@Tag(name = "Receive", 
description = """
            Receive WhatsApp messages using an existing WhatsApp session.

            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
            """)
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
            Receive all unread WhatsApp messages using an existing WhatsApp session.

            🔐 Authentication
            - API Key must be provided in header `X-Api-Key`
            - RapidAPI and internal keys are supported
            - Authentication, client validation and billing are handled by filter
            """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Unread messages retrieved successfully"),
                        @ApiResponse(responseCode = "400", description = "Session not found"),
                        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
                        @ApiResponse(responseCode = "409", description = "Session not connected"),
                        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
                        @ApiResponse(responseCode = "500", description = "Failed to fetch messages from WPPConnect")
        })
        @GetMapping("/{session}/all-unread-messages")
        public ResponseEntity<?> getAllUnreadMessages(
                        @PathVariable String session,
                        HttpServletRequest request) {

                return processMessageRequest(session, null, request);
        }

        @Operation(summary = "Get all messages from a specific chat", description = """
                        Returns all messages exchanged with a specific phone number.

                        ### Authentication
                        - API Key must be provided in header `X-Api-Key`
                        - Authentication is handled by filter
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Chat messages retrieved successfully"),
                        @ApiResponse(responseCode = "400", description = "Session not found"),
                        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
                        @ApiResponse(responseCode = "409", description = "Session not connected"),
                        @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
                        @ApiResponse(responseCode = "500", description = "Failed to fetch messages from WPPConnect")
        })
        @GetMapping("/{session}/all-messages-in-chat/{phone}")
        public ResponseEntity<?> getAllMessagesInChat(
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
        private ResponseEntity<?> processMessageRequest(
                        String sessionName,
                        String phone,
                        HttpServletRequest request) {

                ApiClient client = (ApiClient) request.getAttribute("apiClient");

                Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(sessionName);
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

                if (!sessionUsageService.canSendMessage(sessionName)) {
                        int used = sessionUsageService.getUsageToday(sessionName);
                        return ResponseEntity.status(429).body(Map.of(
                                        "error", "session daily limit exceeded (anti-block protection)",
                                        "limit", 450,
                                        "used", used));
                }

                if (session.getWppToken() == null) {
                        return ResponseEntity.badRequest().body(Map.of(
                                        "error", "wpp token missing for session",
                                        "session", sessionName));
                }

                try {
                        Map<?, ?> response = (phone == null)
                                        ? wppService.getAllUnreadMessages(sessionName, session.getWppToken())
                                        : wppService.getAllMessagesInChat(sessionName, session.getWppToken(), phone);

                        sessionUsageService.recordUsage(sessionName);

                        logger.debug("MESSAGES_RECEIVED | client={} | session={}",
                                        client.getId(), sessionName);

                        return ResponseEntity.ok(response);

                } catch (Exception e) {
                        logger.error("ERROR RECEIVING MESSAGES | session={}", sessionName, e);
                        return ResponseEntity.status(500).body(Map.of(
                                        "error", "failed to fetch messages from WPPConnect",
                                        "message", e.getMessage()));
                }
        }
}
