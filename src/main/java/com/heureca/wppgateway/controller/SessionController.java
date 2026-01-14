package com.heureca.wppgateway.controller;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.dto.CreateSessionRequest;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
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
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
@Tag(name = "Sessions", description = "Create, start and monitor WhatsApp sessions. Each session is uniquely linked to a phone number.")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
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

        // =========================================================
        // CREATE SESSION
        // =========================================================

        @Operation(summary = "Create a WhatsApp session", description = """
                        Creates a new WhatsApp session for a phone number.

                        ### Rules
                        - Each phone number can belong to only ONE client
                        - If the phone already exists for the same client, the token is refreshed
                        - Authentication is handled globally via API Key
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Session created or token refreshed", content = @Content(mediaType = "application/json", examples = @ExampleObject(name = "Session created", value = """
                                        {
                                          "action": "session_created",
                                          "session": "wpp_5511999999999",
                                          "phone": "5511999999999",
                                          "status": "TOKEN_CREATED"
                                        }
                                        """))),
                        @ApiResponse(responseCode = "400", description = "Invalid phone number"),
                        @ApiResponse(responseCode = "403", description = "Phone already registered by another client"),
                        @ApiResponse(responseCode = "401", description = "Missing or invalid API Key")
        })
        @PostMapping("/create-session")
        public ResponseEntity<?> createSession(
                        HttpServletRequest request,
                        @Valid @io.swagger.v3.oas.annotations.parameters.RequestBody(required = true, content = @Content(schema = @Schema(implementation = CreateSessionRequest.class), examples = @ExampleObject(name = "Create session example", value = """
                                        {
                                          "phone": "+55 (11) 99999-9999",
                                          "description": "Main sales WhatsApp"
                                        }
                                        """))) @RequestBody CreateSessionRequest dto) {

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

                return createNewSession(
                                client.getApiKey(),
                                cleanPhone,
                                sessionName,
                                dto.getDescription());
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
                logger.debug("WPPCONNECT RESPONSE: {}", resp);

                s.setStatus(Objects.toString(resp.get("status"), "UNKNOWN"));
                sessionRepository.save(s);

                return ResponseEntity.ok(resp);
        }

        // =========================================================
        // GET SESSION STATUS (PROXY - SOURCE OF TRUTH: WPPCONNECT)
        // =========================================================

        @Operation(summary = "Get WhatsApp session status", description = """
                        Returns the real-time WhatsApp session status directly from the provider (WPPConnect).

                        This endpoint acts as a transparent proxy and does NOT rely on local database state.
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Session status retrieved from provider"),
                        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
                        @ApiResponse(responseCode = "404", description = "Session not found"),
                        @ApiResponse(responseCode = "424", description = "Failed to retrieve status from provider")
        })
        @GetMapping("/{session}/status-session")
        public ResponseEntity<?> getSessionStatus(
                        HttpServletRequest request,
                        @PathVariable String session) {

                ApiClient client = (ApiClient) request.getAttribute("apiClient");

                Optional<SessionEntity> opt = sessionRepository.findBySessionName(session);
                if (opt.isEmpty()) {
                        return ResponseEntity.status(404).body(Map.of(
                                        "error", "session not found",
                                        "session", session));
                }

                SessionEntity s = opt.get();

                if (!s.getClientApiKey().equals(client.getApiKey())) {
                        return ResponseEntity.status(403).body(Map.of(
                                        "error", "session does not belong to client",
                                        "session", session));
                }

                try {
                        // 🔹 SOURCE OF TRUTH: WPPCONNECT
                        Map<?, ?> providerResp = wppService.getSessionStatus(session, s.getWppToken());

                        logger.debug("WPPCONNECT STATUS RESPONSE: {}", providerResp);

                        // 🔹 Gateway only forwards
                        return ResponseEntity.ok(providerResp);

                } catch (Exception e) {
                        logger.error("Failed to retrieve session status from WPPCONNECT", e);
                        return ResponseEntity.status(424).body(Map.of(
                                        "error", "failed_to_fetch_session_status",
                                        "message", e.getMessage()));
                }
        }

        // =========================================================
        // DELETE SESSION
        // =========================================================

        @Operation(summary = "Delete (revoke) a WhatsApp session", description = """
                        Revokes a WhatsApp session and releases the phone number.

                        ### Rules
                        - Only the owner API Key can delete a session
                        - The session is logged out and closed in the WhatsApp provider
                        - After deletion, the phone number can be reused
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Session revoked", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                        {
                                          "action": "session_revoked",
                                          "session": "wpp_5511999999999",
                                          "phone": "5511999999999",
                                          "status": "REVOKED"
                                        }
                                        """))),
                        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
                        @ApiResponse(responseCode = "404", description = "Session not found")
        })
        @DeleteMapping("/session/{session}")
        public ResponseEntity<?> deleteSession(
                        HttpServletRequest request,
                        @PathVariable String session) {

                ApiClient client = (ApiClient) request.getAttribute("apiClient");

                SessionEntity s = sessionRepository.findBySessionName(session)
                                .orElseThrow(() -> new RuntimeException("session not found"));

                if (!s.getClientApiKey().equals(client.getApiKey())) {
                        return ResponseEntity.status(403).body(Map.of(
                                        "error", "session does not belong to client"));
                }

                // Best-effort cleanup on WPPConnect
                try {
                        wppService.safeLogoutAndClose(session, s.getWppToken());
                } catch (Exception e) {
                        logger.warn("Failed to cleanup session on WPPCONNECT: {}", e.getMessage());
                        // NÃO falha o delete por causa do provider
                }

                sessionRepository.delete(s);

                return ResponseEntity.ok(Map.of(
                                "action", "session_revoked",
                                "session", s.getSessionName(),
                                "phone", s.getPhone(),
                                "status", "REVOKED"));
        }

        @Operation(summary = "Get WhatsApp session QR Code (Base64)", description = "Returns the QR Code as a Base64-encoded PNG image.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "QR Code Base64"),
                        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
                        @ApiResponse(responseCode = "404", description = "Session not found")
        })

        // =========================================================
        // GET QR CODE (base64 - INTEGRATION FRIENDLY)
        // =========================================================
        @GetMapping("/{session}/qrcode/base64")
        public ResponseEntity<?> getQrCodeBase64(
                        HttpServletRequest request,
                        @PathVariable String session) {

                ApiClient client = (ApiClient) request.getAttribute("apiClient");

                Optional<SessionEntity> opt = sessionRepository.findBySessionName(session);
                if (opt.isEmpty()) {
                        return ResponseEntity.status(404).body(Map.of(
                                        "error", "session not found",
                                        "session", session));
                }

                SessionEntity s = opt.get();

                if (!s.getClientApiKey().equals(client.getApiKey())) {
                        return ResponseEntity.status(403).body(Map.of(
                                        "error", "session does not belong to client"));
                }

                try {
                        byte[] png = wppService.fetchQrCodeImage(session, s.getWppToken());
                        String base64 = Base64.getEncoder().encodeToString(png);

                        return ResponseEntity.ok(Map.of(
                                        "type", "image",
                                        "format", "png",
                                        "encoding", "base64",
                                        "data", base64,
                                        "size_bytes", png.length,
                                        "message", "Scan with WhatsApp"));

                } catch (Exception e) {
                        logger.error("Failed to retrieve QRCode", e);
                        return ResponseEntity.status(424).body(Map.of(
                                        "error", "failed_to_fetch_qrcode",
                                        "message", e.getMessage()));
                }
        }

        // =========================================================
        // GET QR CODE (IMAGE - BROWSER FRIENDLY)
        // =========================================================

        @Operation(summary = "Get WhatsApp session QR Code (PNG)", description = "Returns the QR Code image for the session. Can be scanned directly.")
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "QR Code image", content = @Content(mediaType = "image/png")),
                        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
                        @ApiResponse(responseCode = "404", description = "Session not found"),
                        @ApiResponse(responseCode = "424", description = "Failed to fetch QR Code from provider")
        })
        @GetMapping(value = "/{session}/qrcode", produces = MediaType.IMAGE_PNG_VALUE)
        public ResponseEntity<?> getQrCodeImage(
                        HttpServletRequest request,
                        @PathVariable String session) {

                ApiClient client = (ApiClient) request.getAttribute("apiClient");

                Optional<SessionEntity> opt = sessionRepository.findBySessionName(session);
                if (opt.isEmpty()) {
                        return ResponseEntity.status(404).body(Map.of(
                                        "error", "session not found",
                                        "session", session));
                }

                SessionEntity s = opt.get();

                if (!s.getClientApiKey().equals(client.getApiKey())) {
                        return ResponseEntity.status(403).body(Map.of(
                                        "error", "session does not belong to client"));
                }

                try {
                        byte[] png = wppService.fetchQrCodeImage(session, s.getWppToken());
                        return ResponseEntity.ok()
                                        .contentType(MediaType.IMAGE_PNG)
                                        .body(png);

                } catch (Exception e) {
                        logger.error("Failed to retrieve QRCode", e);
                        return ResponseEntity.status(424).body(Map.of(
                                        "error", "failed_to_fetch_qrcode",
                                        "message", e.getMessage()));
                }
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

}
