package com.heureca.wppgateway.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.heureca.wppgateway.dto.CreateSessionRequest;
import com.heureca.wppgateway.model.Client;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.ClientService;
import com.heureca.wppgateway.service.UsageService;
import com.heureca.wppgateway.service.WppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Sessions", description = "Manage WhatsApp sessions: create, start, connect, logout and query status")
@SecurityRequirement(name = "ApiKeyAuth")
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);

    private final ClientService clientService;
    private final WppService wppService;
    private final SessionRepository sessionRepository;
    private final UsageService usageService;
    private final RestTemplate restTemplate;

    public SessionController(
            ClientService clientService,
            WppService wppService,
            SessionRepository sessionRepository,
            UsageService usageService,
            RestTemplate restTemplate) {
        this.clientService = clientService;
        this.wppService = wppService;
        this.sessionRepository = sessionRepository;
        this.usageService = usageService;
        this.restTemplate = restTemplate;
    }

    /*
     * ==========================
     * Helpers
     * ==========================
     */

    private Optional<Client> validateApiKey(String apiKey) {
        return clientService.findByApiKey(apiKey);
    }

    private Map<?, ?> callWppConnectEndpoint(String sessionName, String token, String endpoint) {
        String url = String.format("%s/api/%s/%s",
                wppService.getWppBaseUrl(), sessionName, endpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, request, Map.class);

        return response.getBody();
    }

    /*
     * ==========================
     * Endpoints
     * ==========================
     */

    @Operation(summary = "Create a new WhatsApp session", description = "Creates a new session for a phone number or refreshes the token if it already exists.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session created or token refreshed"),
            @ApiResponse(responseCode = "400", description = "Invalid phone format"),
            @ApiResponse(responseCode = "401", description = "Invalid API Key"),
            @ApiResponse(responseCode = "403", description = "Phone already registered by another client")
    })
    @PostMapping("/create-session")
    public ResponseEntity<?> createSession(
            @Parameter(name = "X-Api-Key", description = "Your RapidAPI / client API key", required = true, in = ParameterIn.HEADER) @RequestHeader("X-Api-Key") String apiKey,

            @Valid @RequestBody CreateSessionRequest dto) {

        Optional<Client> clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));
        }

        if (!dto.isPhoneValid()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid phone number format",
                    "phone", dto.getPhone()));
        }

        String cleanPhone = dto.getCleanPhone();
        String sessionName = "wpp_" + cleanPhone;

        Optional<SessionEntity> existingSessionOpt = sessionRepository.findByPhone(cleanPhone);

        if (existingSessionOpt.isPresent()) {
            SessionEntity existingSession = existingSessionOpt.get();

            if (!apiKey.equals(existingSession.getClientApiKey())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "phone already registered by another client",
                        "phone", cleanPhone));
            }

            return updateSessionToken(existingSession, sessionName);
        }

        return createNewSession(apiKey, cleanPhone, sessionName, dto.getDescription());
    }

    @Operation(summary = "Start a WhatsApp session", description = "Starts a WhatsApp session using an existing persisted token. QR Code will only be required if session is invalid.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session started"),
            @ApiResponse(responseCode = "401", description = "Invalid API Key"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "404", description = "Session not found")
    })
    @PostMapping("/start-session/{session}")
    public ResponseEntity<?> startSession(
            @RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session) {

        Optional<Client> clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));
        }

        Optional<SessionEntity> sOpt = sessionRepository.findBySessionName(session);

        if (sOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));
        }

        SessionEntity s = sOpt.get();

        if (!apiKey.equals(s.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
        }

        if (s.getWppToken() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wpp token missing"));
        }

        Map<?, ?> resp = wppService.startSession(session, s.getWppToken());
        logger.info("WPPCONNECT RESPONSE: {}", resp);

        String status = String.valueOf(resp.getOrDefault("status", "UNKNOWN")).toUpperCase();

        if (resp.containsKey("qrcode") || resp.containsKey("urlcode")) {
            status = "QRCODE";
        } else if (resp.toString().toUpperCase().contains("CONNECTED")) {
            status = "CONNECTED";
        }

        s.setStatus(status);
        sessionRepository.save(s);

        return ResponseEntity.ok(resp);
    }

    @Operation(summary = "Get session status", description = "Returns the current status of a WhatsApp session.")
    @GetMapping("/{session}/status-session")
    public ResponseEntity<?> getSessionStatus(
            @RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session) {

        Optional<Client> clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));
        }

        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(session);

        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));
        }

        SessionEntity s = sessionOpt.get();

        if (!apiKey.equals(s.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
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

    private ResponseEntity<?> updateSessionToken(SessionEntity s, String sessionName) {
        Map<?, ?> tokenResp = wppService.generateWppToken(sessionName);
        String token = Objects.toString(tokenResp.get("token"), null);

        s.setWppToken(token);
        s.setStatus("TOKEN_UPDATED");
        s.setCreatedAt(LocalDateTime.now());
        sessionRepository.save(s);

        return ResponseEntity.ok(Map.of(
                "action", "token_updated",
                "session", sessionName,
                "phone", s.getPhone(),
                "status", s.getStatus()));
    }

    private ResponseEntity<?> createNewSession(
            String apiKey,
            String cleanPhone,
            String sessionName,
            String description) {

        SessionEntity s = new SessionEntity();
        s.setSessionName(sessionName);
        s.setClientApiKey(apiKey);
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
}
