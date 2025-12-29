// MediaController.java
package com.example.wppgateway.controller;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.wppgateway.dto.SendFileRequest;
import com.example.wppgateway.dto.SendImageRequest;
import com.example.wppgateway.dto.SendStickerRequest;
import com.example.wppgateway.dto.SendVoiceRequest;
import com.example.wppgateway.model.Client;
import com.example.wppgateway.model.SessionEntity;
import com.example.wppgateway.repository.SessionRepository;
import com.example.wppgateway.service.ClientService;
import com.example.wppgateway.service.SessionUsageService;
import com.example.wppgateway.service.UsageService;
import com.example.wppgateway.service.WppService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/media")
public class MediaController {
    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final ClientService clientService;
    private final UsageService usageService;
    private final SessionUsageService sessionUsageService;
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public MediaController(ClientService clientService,
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

    /**
     * Validações comuns para todos os endpoints de mídia
     */
    private ResponseEntity<?> validateRequest(String apiKey, String sessionName, String requestType) {
        // 1. Validar cliente
        Optional<Client> clientOpt = clientService.findByApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid api key"));
        }
        Client client = clientOpt.get();

        // 2. Validar sessão existe
        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(sessionName);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));
        }

        SessionEntity session = sessionOpt.get();

        // 3. Verificar se sessão pertence ao cliente
        if (!apiKey.equals(session.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
        }

        // 4. Verificar status da sessão
        String sessionStatus = session.getStatus();
        if (!"OPEN".equalsIgnoreCase(sessionStatus) &&
                !"QRCODE".equalsIgnoreCase(sessionStatus) &&
                !"CONNECTED".equalsIgnoreCase(sessionStatus)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "session not ready",
                    "status", sessionStatus));
        }

        // 5. Rate limiting POR CLIENTE (plano)
        int clientUsed = usageService.getUsageToday(apiKey);
        if (clientUsed + 1 > client.getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "client daily limit exceeded",
                    "limit", client.getDailyLimit(),
                    "used", clientUsed));
        }

        // 6. Rate limiting POR SESSÃO (anti-bloqueio - 450/dia)
        if (!sessionUsageService.canSendMessage(sessionName)) {
            int sessionUsed = sessionUsageService.getUsageToday(sessionName);
            return ResponseEntity.status(429).body(Map.of(
                    "error", "session daily limit exceeded (anti-block protection)",
                    "limit", 450,
                    "used", sessionUsed,
                    "session", sessionName));
        }

        // 7. Buscar token
        String token = session.getWppToken();
        if (token == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wpp token missing for session"));
        }

        return ResponseEntity.ok(Map.of(
                "client", client,
                "session", session,
                "token", token));
    }

    /**
     * POST /api/media/send-image
     * Envia imagem (JPG, PNG, etc)
     */
    // No MediaController, modifique o método sendImage:

    @PostMapping("/send-image")
    public ResponseEntity<?> sendImage(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendImageRequest dto) {

        // Validações comuns
        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession(), "send-image");
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        Map<?, ?> validationData = (Map<?, ?>) validation.getBody();
        String token = (String) validationData.get("token");

        try {
            // VALIDAR BASE64 ANTES DE ENVIAR
            if (dto.getBase64() == null || dto.getBase64().trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Base64 is empty",
                        "hint", "Use /api/test/generate-test-base64 to get sample base64"));
            }

            // Verificar se base64 parece válido
            if (!isValidBase64(dto.getBase64())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Invalid base64 format",
                        "hint", "Base64 should contain only A-Z, a-z, 0-9, +, /, =",
                        "first_50_chars", dto.getBase64().substring(0, Math.min(50, dto.getBase64().length()))));
            }

            // Preparar body para WPPConnect
            Map<String, Object> body = Map.of(
                    "phone", dto.getPhone(),
                    "isGroup", dto.isGroup(),
                    "filename", dto.getFilename() != null ? dto.getFilename() : "image.jpg",
                    "caption", dto.getCaption() != null ? dto.getCaption() : "",
                    "base64", dto.getBase64());

            logger.info("Sending image to {} via session {}", dto.getPhone(), dto.getSession());

            // Chamar WPPConnect
            Map<?, ?> wppResponse = wppService.sendImage(dto.getSession(), token, body);

            // Verificar resposta do WPPConnect
            if (wppResponse != null && "error".equalsIgnoreCase(String.valueOf(wppResponse.get("status")))) {
                return ResponseEntity.status(500).body(Map.of(
                        "error", "WPPConnect returned error",
                        "wpp_response", wppResponse));
            }

            // Registrar uso
            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(dto.getSession());

            return ResponseEntity.ok(wppResponse != null ? wppResponse : Map.of("status", "sent"));

        } catch (Exception e) {
            logger.error("Error sending image", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to send image",
                    "message", e.getMessage(),
                    "session", dto.getSession(),
                    "phone", dto.getPhone()));
        }
    }

    // Método auxiliar para validar base64
    private boolean isValidBase64(String base64) {
        try {
            // Remover possíveis prefixos
            String cleanBase64 = base64;
            if (base64.contains(",")) {
                cleanBase64 = base64.split(",")[1];
            }

            // Verificar padrão base64
            if (!cleanBase64.matches("^[A-Za-z0-9+/]*={0,2}$")) {
                return false;
            }

            // Tentar decodificar
            Base64.getDecoder().decode(cleanBase64);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * POST /api/media/send-file
     * Envia documento (PDF, DOC, XLS, etc)
     */
    @PostMapping("/send-file")
    public ResponseEntity<?> sendFile(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendFileRequest dto) {

        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession(), "send-file");
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        Map<?, ?> validationData = (Map<?, ?>) validation.getBody();
        String token = (String) validationData.get("token");

        try {
            // Preparar body para WPPConnect
            Map<String, Object> body = Map.of(
                    "phone", dto.getPhone(),
                    "isGroup", dto.isGroup(),
                    "filename", dto.getFilename(),
                    "caption", dto.getCaption() != null ? dto.getCaption() : "",
                    "base64", dto.getBase64());

            // Chamar WPPConnect
            Map<?, ?> wppResponse = wppService.sendFile(dto.getSession(), token, body);

            // Registrar uso
            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(dto.getSession());

            return ResponseEntity.ok(wppResponse);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to send file",
                    "message", e.getMessage()));
        }
    }

    /**
     * POST /api/media/send-voice
     * Envia áudio (mensagem de voz)
     */
    @PostMapping("/send-voice")
    public ResponseEntity<?> sendVoice(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendVoiceRequest dto) {

        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession(), "send-voice");
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        Map<?, ?> validationData = (Map<?, ?>) validation.getBody();
        String token = (String) validationData.get("token");

        try {
            // Preparar body para WPPConnect
            Map<String, Object> body = Map.of(
                    "phone", dto.getPhone(),
                    "isGroup", dto.isGroup(),
                    "base64Ptt", dto.getBase64Ptt());

            // Chamar WPPConnect
            Map<?, ?> wppResponse = wppService.sendVoice(dto.getSession(), token, body);

            // Registrar uso
            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(dto.getSession());

            return ResponseEntity.ok(wppResponse);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to send voice message",
                    "message", e.getMessage()));
        }
    }

    /**
     * POST /api/media/send-sticker
     * Envia sticker (imagem convertida)
     */
    @PostMapping("/send-sticker")
    public ResponseEntity<?> sendSticker(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendStickerRequest dto) {

        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession(), "send-sticker");
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        Map<?, ?> validationData = (Map<?, ?>) validation.getBody();
        String token = (String) validationData.get("token");

        try {
            // Preparar body para WPPConnect
            Map<String, Object> body = Map.of(
                    "phone", dto.getPhone(),
                    "isGroup", dto.isGroup(),
                    "base64", dto.getBase64());

            // Chamar WPPConnect
            Map<?, ?> wppResponse = wppService.sendSticker(dto.getSession(), token, body);

            // Registrar uso
            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(dto.getSession());

            return ResponseEntity.ok(wppResponse);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to send sticker",
                    "message", e.getMessage()));
        }
    }
}