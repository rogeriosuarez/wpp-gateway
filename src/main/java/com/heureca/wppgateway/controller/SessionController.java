package com.heureca.wppgateway.controller;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.dto.CreateSessionRequest;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.WppService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Sessions", description = "Create, start and monitor WhatsApp sessions. Each session is uniquely linked to a phone number.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class SessionController {

    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);

    private final WppService wppService;
    private final SessionRepository sessionRepository;

    public SessionController(WppService wppService,
            SessionRepository sessionRepository) {
        this.wppService = wppService;
        this.sessionRepository = sessionRepository;
    }

    // =========================================================
    // CREATE SESSION
    // =========================================================

    @Operation(summary = "Create a WhatsApp session", description = """
            Creates a new WhatsApp session for a phone number.

            ### Rules
            - Each phone number can belong to only ONE client
            - If the phone already exists for the same client, the token is refreshed
            - Authentication is handled globally via API Key

            ### Headers
            - X-Api-Key (internal clients)
            - X-RapidAPI-Key (RapidAPI users)
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session created or token refreshed", content = @Content(mediaType = "application/json", examples = {
                    @ExampleObject(name = "Session created", value = """
                            {
                              "action": "session_created",
                              "session": "wpp_5511999999999",
                              "phone": "5511999999999",
                              "status": "TOKEN_CREATED"
                            }
                            """)
            })),
            @ApiResponse(responseCode = "400", description = "Invalid phone number"),
            @ApiResponse(responseCode = "403", description = "Phone already registered by another client"),
            @ApiResponse(responseCode = "401", description = "Missing or invalid API Key")
    })
    @PostMapping("/create-session")
    public ResponseEntity<?> createSession(
            HttpServletRequest request,

            @Parameter(name = "X-Api-Key", description = "Client API Key (internal or RapidAPI)", required = true, in = ParameterIn.HEADER, example = "abc123-your-api-key") @RequestHeader(value = "X-Api-Key", required = false) String ignored,

            @Valid @RequestBody @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = CreateSessionRequest.class), examples = {
                    @ExampleObject(name = "Create session example", value = """
                            {
                              "phone": "+55 (11) 99999-9999",
                              "description": "Main sales WhatsApp"
                            }
                            """)
            })) CreateSessionRequest dto) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        if (!dto.isPhoneValid()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid phone number format",
                    "phone", dto.getPhone()));
        }

        String cleanPhone = dto.getCleanPhone();
        String sessionName = "wpp_" + cleanPhone;

        Optional<SessionEntity> existingOpt = sessionRepository.findByPhone(cleanPhone);

        if (existingOpt.isPresent()) {
            SessionEntity existing = existingOpt.get();

            if (!existing.getClientApiKey().equals(client.getApiKey())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "phone already registered by another client",
                        "phone", cleanPhone));
            }

            return updateSessionToken(existing);
        }

        return createNewSession(client.getApiKey(), cleanPhone, sessionName, dto.getDescription());
    }

    // =========================================================
    // START SESSION
    // =========================================================

    @Operation(summary = "Start a WhatsApp session", description = """
            Starts a previously created WhatsApp session.

            This step is required to generate QR Code or establish connection.
            """)
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

        Map<?, ?> resp = wppService.startSession(session, s.getWppToken());
        logger.info("WPPCONNECT RESPONSE: {}", resp);

        s.setStatus(resolveStatus(resp));
        sessionRepository.save(s);

        return ResponseEntity.ok(resp);
    }

    // =========================================================
    // GET SESSION STATUS
    // =========================================================

    @Operation(summary = "Get WhatsApp session status", description = "Returns current status and metadata of a WhatsApp session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session status retrieved"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "404", description = "Session not found")
    })
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
                "phone", s.getPhone(),
                "status", s.getStatus(),
                "createdAt", s.getCreatedAt(),
                "connected", "CONNECTED".equalsIgnoreCase(s.getStatus())));
    }

    // =========================================================
    // INTERNAL HELPERS
    // =========================================================

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
            String apiKey,
            String phone,
            String sessionName,
            String description) {

        SessionEntity s = new SessionEntity();
        s.setSessionName(sessionName);
        s.setClientApiKey(apiKey);
        s.setPhone(phone);
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
                "phone", phone,
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