package com.heureca.wppgateway.controller;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.dto.SendMessageRequest;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.SessionUsageService;
import com.heureca.wppgateway.service.WppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/messages")
@Tag(name = "Messages", description = "Send WhatsApp messages using an active session")
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

            ### Notes
            - Authentication is handled globally
            - Client validation and billing are handled by filter
            - This endpoint focuses on WhatsApp delivery rules
            - API key must be in header 'X-Api-Key'
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Message sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or missing token"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "409", description = "Session not ready"),
            @ApiResponse(responseCode = "429", description = "Session daily limit exceeded")
    })
    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(
            @Parameter(name = "X-Api-Key", description = "RapidAPI or internal API Key", required = true, in = ParameterIn.HEADER) @RequestHeader("X-Api-Key") String apiKey,

            @Valid @RequestBody SendMessageRequest dto) {

        // 1. Validate session existence
        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(dto.getSession());

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "session not found",
                    "session", dto.getSession()));
        }

        SessionEntity session = sessionOpt.get();

        // 2. Validate session ownership
        if (!apiKey.equals(session.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "session does not belong to client",
                    "session", dto.getSession()));
        }

        // 3. Validate session status
        String status = session.getStatus();
        if (!"OPEN".equalsIgnoreCase(status)
                && !"QRCODE".equalsIgnoreCase(status)
                && !"CONNECTED".equalsIgnoreCase(status)) {

            return ResponseEntity.status(409).body(Map.of(
                    "error", "session not ready",
                    "status", status));
        }

        // 4. Anti-block protection (per session)
        if (!sessionUsageService.canSendMessage(dto.getSession())) {
            int used = sessionUsageService.getUsageToday(dto.getSession());
            return ResponseEntity.status(429).body(Map.of(
                    "error", "session daily limit exceeded (anti-block protection)",
                    "limit", 450,
                    "used", used,
                    "session", dto.getSession()));
        }

        // 5. Validate token
        if (session.getWppToken() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "wpp token missing for session",
                    "session", dto.getSession()));
        }

        // 6. Send message
        Map<?, ?> response = wppService.sendMessage(
                dto.getSession(),
                session.getWppToken(),
                dto.getTo(),
                dto.getMessage());

        // 7. Register session usage
        sessionUsageService.recordUsage(dto.getSession());

        logger.info(
                "MESSAGE_SENT | session={} | to={}",
                dto.getSession(),
                dto.getTo());

        return ResponseEntity.ok(response);
    }
}
