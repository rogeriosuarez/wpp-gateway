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
        // START SESSION (CREATE IF NOT EXISTS)
        // =========================================================

        @Operation(summary = "Start a WhatsApp session", description = """
                        Starts a WhatsApp session for a phone number.

                        This is the main entry point of the API.

                        ### Behavior
                        - If the session does not exist, it is created automatically
                        - If it exists and belongs to the same client, it is reused
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Session started", content = @Content(mediaType = "application/json", examples = @ExampleObject(value = """
                                        {
                                          "action": "session_started",
                                          "session": "wpp_5511999999999",
                                          "phone": "5511999999999",
                                          "status": "WAITING_QRCODE"
                                        }
                                        """))),
                        @ApiResponse(responseCode = "400", description = "Invalid phone number"),
                        @ApiResponse(responseCode = "403", description = "Phone already registered by another client")
        })
        @PostMapping("/start-session/{phone}")
        public ResponseEntity<?> startSession(
                        HttpServletRequest request,
                        @PathVariable String phone) {

                ApiClient client = (ApiClient) request.getAttribute("apiClient");

                String cleanPhone = phone.replaceAll("\\D", "");
                if (cleanPhone.length() < 10) {
                        return ResponseEntity.badRequest().body(Map.of(
                                        "error", "invalid phone number",
                                        "phone", phone));
                }

                // 🔑 Identidade real: client + phone
                Optional<SessionEntity> opt = sessionRepository.findByClientApiKeyAndPhone(
                                client.getApiKey(),
                                cleanPhone);

                SessionEntity session;

                if (opt.isPresent()) {
                        session = opt.get();
                } else {
                        // 🔹 Criação implícita
                        String sessionName = "wpp_" + cleanPhone;

                        session = new SessionEntity();
                        session.setClientApiKey(client.getApiKey());
                        session.setPhone(cleanPhone);
                        session.setSessionName(sessionName);
                        session.setStatus("CREATED");

                        sessionRepository.save(session);

                        Map<?, ?> tokenResp = wppService.generateWppToken(sessionName);
                        session.setWppToken(Objects.toString(tokenResp.get("token"), null));
                        session.setStatus("TOKEN_CREATED");

                        sessionRepository.save(session);
                }

                // 🔥 Start real no provider
                Map<?, ?> providerResp = wppService.startSession(
                                session.getSessionName(),
                                session.getWppToken());

                session.setStatus(
                                Objects.toString(providerResp.get("status"), "UNKNOWN"));
                sessionRepository.save(session);

                return ResponseEntity.ok(Map.of(
                                "session", session.getSessionName(),
                                "phone", session.getPhone(),
                                "provider", providerResp));
        }

        // =========================================================
        // GET SESSION STATUS (PROXY - SOURCE OF TRUTH: WPPCONNECT)
        // =========================================================

        @Operation(summary = "Get WhatsApp session status", description = """
                        Returns the real-time WhatsApp session status directly from the provider (WPPConnect).

                        This endpoint acts as a transparent proxy and does NOT rely on local database state.
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Session status retrieved"),
                        @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
                        @ApiResponse(responseCode = "404", description = "Session not found"),
                        @ApiResponse(responseCode = "424", description = "Failed to retrieve status")
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
                        logger.error("Failed to retrieve session status", e);
                        return ResponseEntity.status(424).body(Map.of(
                                        "error", "failed_to_fetch_session_status",
                                        "message", e.getMessage()));
                }
        }

        // =========================================================
        // DELETE SESSION (LOGOUT)
        // =========================================================

        @Operation(summary = "Logout a WhatsApp session", description = """
                        Logs out a WhatsApp session and releases the phone number.

                        - Only the owner can logout
                        - Session is closed in the provider
                        """)
        @ApiResponses({
                        @ApiResponse(responseCode = "200", description = "Session logged out"),
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

                try {
                        wppService.safeLogoutAndClose(
                                        session,
                                        s.getWppToken());
                } catch (Exception e) {
                        logger.warn(
                                        "Failed to cleanup session on provider: {}",
                                        e.getMessage());
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
        // GET QR CODE (BASE64)
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
                        byte[] png = wppService.fetchQrCodeImage(
                                        session,
                                        s.getWppToken());

                        return ResponseEntity.ok(Map.of(
                                        "type", "image",
                                        "format", "png",
                                        "encoding", "base64",
                                        "data", Base64.getEncoder().encodeToString(png),
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
        // GET QR CODE (IMAGE)
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
                        byte[] png = wppService.fetchQrCodeImage(
                                        session,
                                        s.getWppToken());

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
}
