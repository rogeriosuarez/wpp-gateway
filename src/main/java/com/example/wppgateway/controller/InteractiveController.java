// InteractiveController.java
package com.example.wppgateway.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.wppgateway.dto.ListRow;
import com.example.wppgateway.dto.ListSection;
import com.example.wppgateway.dto.SendButtonsRequest;
import com.example.wppgateway.dto.SendListRequest;
import com.example.wppgateway.dto.SendPollRequest;
import com.example.wppgateway.dto.SendReplyRequest;
import com.example.wppgateway.model.Client;
import com.example.wppgateway.model.SessionEntity;
import com.example.wppgateway.repository.SessionRepository;
import com.example.wppgateway.service.ClientService;
import com.example.wppgateway.service.SessionUsageService;
import com.example.wppgateway.service.UsageService;
import com.example.wppgateway.service.WppService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/interactive")
public class InteractiveController {

    private final ClientService clientService;
    private final UsageService usageService;
    private final SessionUsageService sessionUsageService;
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public InteractiveController(ClientService clientService,
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
     * POST /api/interactive/send-list
     * Envia lista interativa (menu de opções)
     */
    @PostMapping("/send-list")
    public ResponseEntity<?> sendList(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendListRequest dto) {

        // Validações comuns (similar ao MediaController)
        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession(), "send-list");
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        Map<?, ?> validationData = (Map<?, ?>) validation.getBody();
        String token = (String) validationData.get("token");
        SessionEntity session = (SessionEntity) validationData.get("session");

        try {
            // Preparar body para WPPConnect
            Map<String, Object> body = Map.of(
                    "phone", dto.getPhone(),
                    "isGroup", dto.isGroup(),
                    "buttonText", dto.getButtonText(),
                    "description", dto.getDescription() != null ? dto.getDescription() : "",
                    "sections", convertSections(dto.getSections()));

            // Chamar WPPConnect
            Map<?, ?> wppResponse = wppService.sendListMessage(dto.getSession(), token, body);

            // Registrar uso
            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(dto.getSession());

            return ResponseEntity.ok(wppResponse);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to send interactive list",
                    "message", e.getMessage(),
                    "session", dto.getSession()));
        }
    }

    /**
     * Validações comuns (reutilizar do MediaController)
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

        // 5. Rate limiting POR CLIENTE
        int clientUsed = usageService.getUsageToday(apiKey);
        if (clientUsed + 1 > client.getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "client daily limit exceeded",
                    "limit", client.getDailyLimit(),
                    "used", clientUsed));
        }

        // 6. Rate limiting POR SESSÃO (anti-bloqueio)
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
     * Converter DTO sections para formato do WPPConnect
     */
    private Object convertSections(java.util.List<ListSection> sections) {
        return sections.stream()
                .map(section -> Map.of(
                        "title", section.getTitle(),
                        "rows", convertRows(section.getRows())))
                .collect(java.util.stream.Collectors.toList());
    }

    private Object convertRows(java.util.List<ListRow> rows) {
        return rows.stream()
                .map(row -> {
                    Map<String, Object> rowMap = new java.util.HashMap<>();
                    rowMap.put("rowId", row.getRowId());
                    rowMap.put("title", row.getTitle());
                    if (row.getDescription() != null && !row.getDescription().isEmpty()) {
                        rowMap.put("description", row.getDescription());
                    }
                    return rowMap;
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * POST /api/interactive/send-buttons
     * Envia botões interativos (deprecated mas funciona)
     */
    @PostMapping("/send-buttons")
    public ResponseEntity<?> sendButtons(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendButtonsRequest dto) {

        // Validações comuns
        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession(), "send-buttons");
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        Map<?, ?> validationData = (Map<?, ?>) validation.getBody();
        String token = (String) validationData.get("token");

        try {
            // Preparar body para WPPConnect
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("phone", dto.getPhone());
            body.put("isGroup", dto.isGroup());
            body.put("message", dto.getMessage());
            body.put("title", dto.getTitle());

            // Converter botões para o formato do WPPConnect
            List<Map<String, String>> buttons = dto.getButtons().stream()
                    .map(btn -> Map.of(
                            "buttonId", btn.getButtonId(),
                            "buttonText", btn.getButtonText()))
                    .collect(java.util.stream.Collectors.toList());
            body.put("buttons", buttons);

            // Chamar WPPConnect
            Map<?, ?> wppResponse = wppService.sendButtons(dto.getSession(), token, body);

            // Registrar uso
            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(dto.getSession());

            return ResponseEntity.ok(wppResponse);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to send interactive buttons",
                    "message", e.getMessage(),
                    "session", dto.getSession()));
        }
    }

    /**
     * POST /api/interactive/send-poll
     * Envia enquete interativa
     */
    @PostMapping("/send-poll")
    public ResponseEntity<?> sendPoll(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendPollRequest dto) {

        // Validações comuns
        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession(), "send-poll");
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        Map<?, ?> validationData = (Map<?, ?>) validation.getBody();
        String token = (String) validationData.get("token");

        try {
            // Preparar body para WPPConnect
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("phone", dto.getPhone());
            body.put("isGroup", dto.isGroup());
            body.put("name", dto.getName());
            body.put("choices", dto.getChoices());

            // Adicionar opções se existirem
            if (dto.getOptions() != null && !dto.getOptions().isEmpty()) {
                body.put("options", dto.getOptions());
            } else {
                // Opções padrão (selecionável única)
                body.put("options", Map.of("selectableCount", 1));
            }

            // Chamar WPPConnect
            Map<?, ?> wppResponse = wppService.sendPollMessage(dto.getSession(), token, body);

            // Registrar uso
            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(dto.getSession());

            return ResponseEntity.ok(wppResponse);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to send poll",
                    "message", e.getMessage(),
                    "session", dto.getSession()));
        }
    }

    /**
     * POST /api/interactive/send-order
     * Envia mensagem de pedido (opcional - para e-commerce)
     */
    @PostMapping("/send-order")
    public ResponseEntity<?> sendOrder(@RequestHeader("X-Api-Key") String apiKey,
            @RequestBody Map<String, Object> requestBody) {

        // Extrair session do body
        String session = (String) requestBody.get("session");
        if (session == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "session is required"));
        }

        // Validações comuns
        ResponseEntity<?> validation = validateRequest(apiKey, session, "send-order");
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        Map<?, ?> validationData = (Map<?, ?>) validation.getBody();
        String token = (String) validationData.get("token");

        try {
            // Chamar WPPConnect (usando send-order-message)
            Map<?, ?> wppResponse = wppService.sendOrderMessage(session, token, requestBody);

            // Registrar uso
            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(session);

            return ResponseEntity.ok(wppResponse);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to send order message",
                    "message", e.getMessage(),
                    "session", session));
        }
    }

    /**
     * POST /api/interactive/send-reply
     * Envia mensagem com botões de resposta rápida (FUNCIONA!)
     */
    @PostMapping("/send-reply")
    public ResponseEntity<?> sendReply(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendReplyRequest dto) {

        ResponseEntity<?> validation = validateRequest(apiKey, dto.getSession(), "send-reply");
        if (!validation.getStatusCode().is2xxSuccessful()) {
            return validation;
        }

        Map<?, ?> validationData = (Map<?, ?>) validation.getBody();
        String token = (String) validationData.get("token");

        try {
            // Preparar body para WPPConnect (send-reply com botões)
            Map<String, Object> body = new java.util.HashMap<>();
            body.put("phone", dto.getPhone());
            body.put("isGroup", dto.isGroup());
            body.put("message", dto.getMessage());

            // Adicionar botões como options
            Map<String, Object> options = new java.util.HashMap<>();

            List<Map<String, String>> buttons = dto.getButtons().stream()
                    .map(btn -> Map.of(
                            "buttonId", btn.getId(),
                            "buttonText", btn.getText()))
                    .collect(java.util.stream.Collectors.toList());

            options.put("buttons", buttons);
            body.put("options", options);

            // Chamar WPPConnect
            Map<?, ?> wppResponse = wppService.sendReply(dto.getSession(), token, body);

            // Registrar uso
            usageService.increment(apiKey, 1);
            sessionUsageService.recordUsage(dto.getSession());

            return ResponseEntity.ok(wppResponse);

        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to send reply buttons",
                    "message", e.getMessage(),
                    "session", dto.getSession()));
        }
    }
}