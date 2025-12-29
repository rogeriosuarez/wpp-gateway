// MessageReceiveController.java
package com.example.wppgateway.controller;

import java.util.Map;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.wppgateway.model.Client;
import com.example.wppgateway.model.SessionEntity;
import com.example.wppgateway.repository.SessionRepository;
import com.example.wppgateway.service.ClientService;
import com.example.wppgateway.service.UsageService;
import com.example.wppgateway.service.WppService;

@RestController
@RequestMapping("/api/receive")
public class MessageReceiveController {

    private final ClientService clientService;
    private final UsageService usageService;
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public MessageReceiveController(ClientService clientService,
            UsageService usageService,
            SessionRepository sessionRepository,
            WppService wppService) {
        this.clientService = clientService;
        this.usageService = usageService;
        this.sessionRepository = sessionRepository;
        this.wppService = wppService;
    }

    /**
     * GET /api/receive/{session}/all-unread-messages
     * Exatamente igual ao endpoint do WPPConnect
     */
    @GetMapping("/{session}/all-unread-messages")
    public ResponseEntity<?> getAllUnreadMessages(
            @RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session) {

        return processMessageRequest(apiKey, session, null, "all-unread");
    }

    /**
     * GET /api/receive/{session}/all-messages-in-chat/{phone}
     * Exatamente igual ao endpoint do WPPConnect
     */
    @GetMapping("/{session}/all-messages-in-chat/{phone}")
    public ResponseEntity<?> getAllMessagesInChat(
            @RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session,
            @PathVariable String phone) {

        return processMessageRequest(apiKey, session, phone, "chat-specific");
    }

    /**
     * Método comum para processar ambas as requisições
     */
    private ResponseEntity<?> processMessageRequest(String apiKey,
            String sessionName,
            String phone,
            String requestType) {

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

        // 4. Verificar status da sessão (deve estar CONNECTED)
        String sessionStatus = session.getStatus();
        if (!"CONNECTED".equalsIgnoreCase(sessionStatus)) {
            return ResponseEntity.status(409).body(Map.of(
                    "error", "session not connected",
                    "status", sessionStatus,
                    "hint", "Session must be connected to receive messages"));
        }

        // 5. Rate limiting POR CLIENTE (plano)
        int clientUsed = usageService.getUsageToday(apiKey);
        if (clientUsed + 1 > client.getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "client daily limit exceeded",
                    "limit", client.getDailyLimit(),
                    "used", clientUsed));
        }

        // 6. Buscar token da sessão
        String token = session.getWppToken();
        if (token == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wpp token missing for session"));
        }

        try {
            Map<?, ?> wppResponse;

            // 7. Chamar endpoint apropriado do WPPConnect
            if ("chat-specific".equals(requestType) && phone != null) {
                wppResponse = wppService.getAllMessagesInChat(sessionName, token, phone);
            } else {
                wppResponse = wppService.getAllUnreadMessages(sessionName, token);
            }

            // 8. Registrar uso (1 request)
            usageService.increment(apiKey, 1);

            // 9. Retornar resposta EXATA do WPPConnect (proxy transparente)
            return ResponseEntity.ok(wppResponse);

        } catch (Exception e) {
            // Log do erro mas retornar erro genérico
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to fetch messages from WPPConnect",
                    "message", e.getMessage(),
                    "session", sessionName,
                    "request_type", requestType));
        }
    }
}