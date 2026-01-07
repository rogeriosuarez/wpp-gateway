package com.heureca.wppgateway.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heureca.wppgateway.dto.CreateSessionRequest;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.WppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Sessions", description = "Manage WhatsApp sessions")
@SecurityRequirement(name = "ApiKeyAuth")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);

    private final WppService wppService;
    private final SessionRepository sessionRepository;

    public SessionController(
            WppService wppService,
            SessionRepository sessionRepository) {
        this.wppService = wppService;
        this.sessionRepository = sessionRepository;
    }

    /*
     * ==========================
     * Create Session
     * ==========================
     */

    @Operation(summary = "Create a new WhatsApp session. API key must be in header 'X-Api-Key'")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session created or token refreshed"),
            @ApiResponse(responseCode = "400", description = "Invalid phone format"),
            @ApiResponse(responseCode = "403", description = "Phone already registered by another client")
    })
    @PostMapping("/create-session")
    public ResponseEntity<?> createSession(
            HttpServletRequest request,
            @Valid @RequestBody CreateSessionRequest dto) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        if (!dto.isPhoneValid()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid phone number format",
                    "phone", dto.getPhone()));
        }

        String cleanPhone = dto.getCleanPhone();
        String sessionName = "wpp_" + cleanPhone;

        Optional<SessionEntity> existingSessionOpt = sessionRepository.findByPhone(cleanPhone);

        if (existingSessionOpt.isPresent()) {
            SessionEntity existing = existingSessionOpt.get();

            if (!existing.getClientApiKey().equals(client.getApiKey())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "phone already registered by another client",
                        "phone", cleanPhone));
            }

            return updateSessionToken(existing);
        }

        return createNewSession(client, cleanPhone, sessionName, dto.getDescription());
    }

    /*
     * ==========================
     * Start Session
     * ==========================
     */

    @Operation(summary = "Start a WhatsApp session. API key must be in header 'X-Api-Key'")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session started"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "404", description = "Session not found")
    })
    @PostMapping("/start-session/{session}")
    public ResponseEntity<?> startSession(
            HttpServletRequest request,
            @PathVariable String session) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        SessionEntity s = sessionRepository.findBySessionName(session)
                .orElseThrow(() -> new RuntimeException("session not found"));

        if (!s.getClientApiKey().equals(client.getApiKey())) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "session does not belong to client"));
        }

        if (s.getWppToken() == null) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "wpp token missing"));
        }

        Map<?, ?> resp = wppService.startSession(session, s.getWppToken());
        logger.info("WPPCONNECT RESPONSE: {}", resp);

        String status = resolveStatus(resp);

        s.setStatus(status);
        sessionRepository.save(s);

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Get Session Status
     * ==========================
     */

    @Operation(summary = "Get session status. API key must be in header 'X-Api-Key'")
    @GetMapping("/{session}/status-session")
    public ResponseEntity<?> getSessionStatus(
            HttpServletRequest request,
            @PathVariable String session) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        SessionEntity s = sessionRepository.findBySessionName(session)
                .orElseThrow(() -> new RuntimeException("session not found"));

        if (!s.getClientApiKey().equals(client.getApiKey())) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "session does not belong to client"));
        }

        return ResponseEntity.ok(Map.of(
                "session", s.getSessionName(),
                "status", s.getStatus(),
                "phone", s.getPhone(),
                "createdAt", s.getCreatedAt(),
                "connected", "CONNECTED".equalsIgnoreCase(s.getStatus())));
    }

    /*
     * ==========================
     * Internals
     * ==========================
     */

    private ResponseEntity<?> updateSessionToken(SessionEntity s) {
        Map<?, ?> tokenResp = wppService.generateWppToken(s.getSessionName());

        s.setWppToken(Objects.toString(tokenResp.get("token"), null));
        s.setStatus("TOKEN_UPDATED");
        s.setCreatedAt(LocalDateTime.now());

        sessionRepository.save(s);

        return ResponseEntity.ok(Map.of(
                "action", "token_updated",
                "session", s.getSessionName(),
                "phone", s.getPhone(),
                "status", s.getStatus()));
    }

    private ResponseEntity<?> createNewSession(
            ApiClient client,
            String cleanPhone,
            String sessionName,
            String description) {

        SessionEntity s = new SessionEntity();
        s.setSessionName(sessionName);
        s.setClientApiKey(client.getApiKey());
        s.setPhone(cleanPhone);
        s.setDescription(description);
        s.setStatus("CREATED");

        sessionRepository.save(s);

        Map<?, ?> tokenResp = wppService.generateWppToken(sessionName);
        s.setWppToken(Objects.toString(tokenResp.get("token"), null));
        s.setStatus("TOKEN_CREATED");

        sessionRepository.save(s);

        return ResponseEntity.ok(Map.of(
                "action", "session_created",
                "session", sessionName,
                "phone", cleanPhone,
                "status", s.getStatus()));
    }

    private String resolveStatus(Map<?, ?> resp) {
        if (resp.containsKey("qrcode") || resp.containsKey("urlcode")) {
            return "QRCODE";
        }
        if (resp.toString().toUpperCase().contains("CONNECTED")) {
            return "CONNECTED";
        }
        return "UNKNOWN";
    }
}
