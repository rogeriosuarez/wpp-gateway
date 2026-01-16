package com.heureca.wppgateway.controller;

import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.SessionUsageService;
import com.heureca.wppgateway.service.WppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/messages")
@Tag(name = "Messages", description = "Send WhatsApp text messages")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class MessageController {

        private static final Logger logger = LoggerFactory.getLogger(MessageController.class);

        private final SessionUsageService sessionUsageService;
        private final SessionRepository sessionRepository;
        private final WppService wppService;

        public MessageController(
                        SessionUsageService sessionUsageService,
                        SessionRepository sessionRepository,
                        WppService wppService) {
                this.sessionUsageService = sessionUsageService;
                this.sessionRepository = sessionRepository;
                this.wppService = wppService;
        }

        @Operation(summary = "Send a WhatsApp text message", description = """
                        Sends a text message using an existing WhatsApp session.

                        🔐 Authentication
                        - API Key must be provided in header `X-Api-Key`
                        - RapidAPI and internal keys are supported
                        - Authentication, client validation and billing are handled by filter
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Message sent successfully"),
                        @ApiResponse(responseCode = "400", description = "Invalid request"),
                        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
                        @ApiResponse(responseCode = "409", description = "Session not ready"),
                        @ApiResponse(responseCode = "429", description = "Session daily limit exceeded")
        })
        @PostMapping("/send")
        public ResponseEntity<?> sendMessage(
                        @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(examples = @ExampleObject(name = "Send text message example", value = """
                                        {
                                          "session": "my-session-01",
                                          "phone": "5521999998888",
                                          "message": "Hello! This message was sent via WPP Gateway 🚀"
                                        }
                                        """))) @RequestBody Map<String, Object> body,
                        HttpServletRequest request) {

                // 🔐 Client already validated by filter
                ApiClient client = (ApiClient) request.getAttribute("apiClient");

                // 1️⃣ Extract session (minimum validation we still need)
                String sessionName = (String) body.get("session");
                if (sessionName == null || sessionName.isBlank()) {
                        return ResponseEntity.badRequest().body(Map.of(
                                        "error", "missing session in request body"));
                }

                // 2️⃣ Validate session existence
                Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(sessionName);

                if (sessionOpt.isEmpty()) {
                        return ResponseEntity.badRequest().body(Map.of(
                                        "error", "session not found",
                                        "session", sessionName));
                }

                SessionEntity session = sessionOpt.get();

                // 3️⃣ Validate ownership
                if (!session.getClientApiKey().equals(client.getApiKey())) {
                        return ResponseEntity.status(403).body(Map.of(
                                        "error", "session does not belong to client",
                                        "session", sessionName));
                }

                // 4️⃣ Anti-block protection (session-level)
                if (!sessionUsageService.canSendMessage(sessionName)) {
                        int used = sessionUsageService.getUsageToday(sessionName);
                        return ResponseEntity.status(429).body(Map.of(
                                        "error", "session daily limit exceeded (anti-block protection)",
                                        "limit", 450,
                                        "used", used,
                                        "session", sessionName));
                }

                // 5️⃣ Validate token presence
                if (session.getWppToken() == null) {
                        return ResponseEntity.badRequest().body(Map.of(
                                        "error", "wpp token missing for session",
                                        "session", sessionName));
                }

                // 6️⃣ Forward AS-IS to WPPConnect
                ResponseEntity<?> response = wppService.sendMessage(
                                sessionName,
                                session.getWppToken(),
                                body);

                // 7️⃣ Register usage (only after provider call)
                sessionUsageService.recordUsage(sessionName);

                logger.debug(
                                "MESSAGE_FORWARD | client={} | session={}",
                                client.getId(),
                                sessionName);

                // 🔥 Return provider response AS-IS
                return response;
        }

}
