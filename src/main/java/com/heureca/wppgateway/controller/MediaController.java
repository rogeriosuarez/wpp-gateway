package com.heureca.wppgateway.controller;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.heureca.wppgateway.config.OpenApiConfig;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.SessionEntity;
import com.heureca.wppgateway.repository.SessionRepository;
import com.heureca.wppgateway.service.SessionUsageService;
import com.heureca.wppgateway.service.UsageService;
import com.heureca.wppgateway.service.WppService;
import com.heureca.wppgateway.dto.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/media")
@Tag(name = "Media", description = "Send media messages via WhatsApp (image, file, voice and sticker)")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME_NAME)
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final UsageService usageService;
    private final SessionUsageService sessionUsageService;
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public MediaController(
            UsageService usageService,
            SessionUsageService sessionUsageService,
            SessionRepository sessionRepository,
            WppService wppService) {
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
    private ResponseEntity<?> validateRequest(ApiClient client, String sessionName) {

        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(sessionName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));
        }

        SessionEntity session = sessionOpt.get();

        if (!client.getApiKey().equals(session.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of(
                    "error", "session does not belong to client"));
        }

        String status = session.getStatus();
        if (!"OPEN".equalsIgnoreCase(status)
                && !"QRCODE".equalsIgnoreCase(status)
                && !"CONNECTED".equalsIgnoreCase(status)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "session not ready",
                    "status", status));
        }

        int clientUsed = usageService.getUsageToday(client.getApiKey());
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
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "wpp token missing for session"));
        }

        return ResponseEntity.ok(Map.of(
                "token", session.getWppToken()));
    }

    /*
     * ==========================
     * Send Image
     * ==========================
     */
    @Operation(summary = "Send an image via WhatsApp")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Image sent successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or base64"),
            @ApiResponse(responseCode = "403", description = "Session does not belong to client"),
            @ApiResponse(responseCode = "409", description = "Session not ready"),
            @ApiResponse(responseCode = "429", description = "Daily limit exceeded"),
            @ApiResponse(responseCode = "500", description = "Failed to send image")
    })
    @PostMapping("/send-image")
    public ResponseEntity<?> sendImage(
            @Valid @RequestBody SendImageRequest dto,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        ResponseEntity<?> validation = validateRequest(client, dto.getSession());
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

            usageService.increment(client.getApiKey(), 1);
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
    @Operation(summary = "Send a document file via WhatsApp")
    @PostMapping("/send-file")
    public ResponseEntity<?> sendFile(
            @Valid @RequestBody SendFileRequest dto,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        ResponseEntity<?> validation = validateRequest(client, dto.getSession());
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

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(dto.getSession());

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Send Voice
     * ==========================
     */
    @Operation(summary = "Send a voice message (PTT) via WhatsApp")
    @PostMapping("/send-voice")
    public ResponseEntity<?> sendVoice(
            @Valid @RequestBody SendVoiceRequest dto,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        ResponseEntity<?> validation = validateRequest(client, dto.getSession());
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

        usageService.increment(client.getApiKey(), 1);
        sessionUsageService.recordUsage(dto.getSession());

        return ResponseEntity.ok(resp);
    }

    /*
     * ==========================
     * Send Sticker
     * ==========================
     */
    @Operation(summary = "Send a sticker via WhatsApp")
    @PostMapping("/send-sticker")
    public ResponseEntity<?> sendSticker(
            @Valid @RequestBody SendStickerRequest dto,
            HttpServletRequest request) {

        ApiClient client = (ApiClient) request.getAttribute("apiClient");

        ResponseEntity<?> validation = validateRequest(client, dto.getSession());
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

        usageService.increment(client.getApiKey(), 1);
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
