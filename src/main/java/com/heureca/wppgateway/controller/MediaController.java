package com.heureca.wppgateway.controller;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.dto.SendFileRequest;
import com.heureca.wppgateway.dto.SendImageRequest;
import com.heureca.wppgateway.dto.SendStickerRequest;
import com.heureca.wppgateway.dto.SendVoiceRequest;
import com.heureca.wppgateway.model.Client;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.ClientService;
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
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/media")
@Tag(name = "Media", description = "Send media messages via WhatsApp (image, file, voice and sticker)")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final ClientService clientService;
    private final UsageService usageService;
    private final SessionUsageService sessionUsageService;
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public MediaController(
            ClientService clientService,
            UsageService usageService,
            SessionUsageService sessionUsageService,
            SessionRepository sessionRepository,
            WppService wppService) {
        this.clientService = clientService;
        this.usageService = usageService;
        this.sessionUsageService = sessionUsageService;
        this.sessionRepository = sessionRepository;
        this.wppService = wppService;
    }

    /*
     * ==========================
     * Common validations
     * ==========================
     */

    private ResponseEntity<?> validateRequest(String apiKey, String sessionName) {

        Optional<Client> clientOpt = clientService.findByApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid api key"));
        }
        Client client = clientOpt.get();

        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(sessionName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));
        }

        SessionEntity session = sessionOpt.get();

        if (!apiKey.equals(session.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
        }

        String status = session.getStatus();
        if (!"OPEN".equalsIgnoreCase(status)
                && !"QRCODE".equalsIgnoreCase(status)
                && !"CONNECTED".equalsIgnoreCase(status)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "session not ready",
                    "status", status));
        }

        int clientUsed = usageService.getUsageToday(apiKey);
        if (clientUsed + 1 > client.getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "client daily limit exceeded",
                    "limit", client.getDailyLimit(),
                    "used", clientUsed));
        }

        if (!sessionUsageService.canSendMessage(sessionName)) {
            int sessionUsed = sessionUsageService.getUsageToday(sessionName);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "session daily limit exceeded (anti-block protection)",
                    "limit", 450,
                    "used", sessionUsed,
                    "session", sessionName));
        }

        if (session.getWppToken() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wpp token missing for session"));
        }

        return ResponseEntity.ok(Map.of(
                "token", session.getWppToken()));
    }

    /*
     * ==========================
     * Send Image
     * ==========================
     */

    @Operation(summary = "Send an image via WhatsApp", description = """
            Sends an image (JPG, PNG, WEBP) to a WhatsApp contact or group.

            ### Notes
            - Base64 must be valid
            - Counts as 1 request
            - Session anti-block limit applies (450/day)
            - API key must be in header 'X-Api-Key'
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or base64"),
            @ApiResponse(responseCode = "401", description = "Invalid API Key"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "409", description = "Session not ready"),
            @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Failed to send image")
    })
    @PostMapping("/send-image")
    public ResponseEntity<?> sendImage(
            @Parameter(name = "X-Api-Key", required = true, in = ParameterIn.HEADER) @RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendImageRequest dto) {

        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession());
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");

        if (dto.getBase64() == null || dto.getBase64().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "base64 is empty"));
        }

        if (!isValidBase64(dto.getBase64())) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid base64 format"));
        }

        try {
            Map<?, ?> resp = wppService.sendImage(
                    dto.getSession(),
                    token,
                    Map.of(
                            "phone", dto.getPhone(),
                            "isGroup", dto.isGroup(),
                            "filename", dto.getFilename() != null ? dto.getFilename() : "image.jpg",
                            "caption", dto.getCaption() != null ? dto.getCaption() : "",
                            "base64", dto.getBase64()));

            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(dto.getSession());

            return ResponseEntity.ok(resp);

        } catch (Exception e) {
            logger.error("Send image failed", e);
            return ResponseEntity.status(500).body(Map.of("error", "failed to send image"));
        }
    }

    /*
     * ==========================
     * Send File
     * ==========================
     */

    @Operation(summary = "Send a document file via WhatsApp. API key must be in header 'X-Api-Key'")
    @PostMapping("/send-file")
    public ResponseEntity<?> sendFile(
            @RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendFileRequest dto) {

        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession());
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");

        Map<?, ?> resp = wppService.sendFile(
                dto.getSession(),
                token,
                Map.of(
                        "phone", dto.getPhone(),
                        "isGroup", dto.isGroup(),
                        "filename", dto.getFilename(),
                        "caption", dto.getCaption() != null ? dto.getCaption() : "",
                        "base64", dto.getBase64()));

        usageService.increment(apiKey, 1);
        sessionUsageService.recordUsage(dto.getSession());

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Send Voice
     * ==========================
     */

    @Operation(summary = "Send a voice message (PTT) via WhatsApp. API key must be in header 'X-Api-Key'")
    @PostMapping("/send-voice")
    public ResponseEntity<?> sendVoice(
            @RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendVoiceRequest dto) {

        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession());
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");

        Map<?, ?> resp = wppService.sendVoice(
                dto.getSession(),
                token,
                Map.of(
                        "phone", dto.getPhone(),
                        "isGroup", dto.isGroup(),
                        "base64Ptt", dto.getBase64Ptt()));

        usageService.increment(apiKey, 1);
        sessionUsageService.recordUsage(dto.getSession());

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Send Sticker
     * ==========================
     */

    @Operation(summary = "Send a sticker via WhatsApp. API key must be in header 'X-Api-Key'")
    @PostMapping("/send-sticker")
    public ResponseEntity<?> sendSticker(
            @RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendStickerRequest dto) {

        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession());
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        String token = (String) ((Map<?, ?>) validation.getBody()).get("token");

        Map<?, ?> resp = wppService.sendSticker(
                dto.getSession(),
                token,
                Map.of(
                        "phone", dto.getPhone(),
                        "isGroup", dto.isGroup(),
                        "base64", dto.getBase64()));

        usageService.increment(apiKey, 1);
        sessionUsageService.recordUsage(dto.getSession());

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Utils
     * ==========================
     */

    private boolean isValidBase64(String base64) {
        try {
            String clean = base64.contains(",") ? base64.split(",")[1] : base64;
            Base64.getDecoder().decode(clean);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
