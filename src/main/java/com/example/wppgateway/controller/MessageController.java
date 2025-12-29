package com.example.wppgateway.controller;

import com.example.wppgateway.model.Client;
import com.example.wppgateway.model.SessionEntity;
import com.example.wppgateway.service.ClientService;
import com.example.wppgateway.service.UsageService;
import com.example.wppgateway.service.WppService;
import com.example.wppgateway.service.SessionUsageService; // IMPORTANTE!
import com.example.wppgateway.repository.SessionRepository;
import com.example.wppgateway.dto.SendMessageRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/messages")
public class MessageController {
    private final ClientService clientService;
    private final UsageService usageService;
    private final SessionUsageService sessionUsageService; // ADICIONADO
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public MessageController(ClientService clientService,
            UsageService usageService,
            SessionUsageService sessionUsageService, // ADICIONADO
            SessionRepository sessionRepository,
            WppService wppService) {
        this.clientService = clientService;
        this.usageService = usageService;
        this.sessionUsageService = sessionUsageService; // ADICIONADO
        this.sessionRepository = sessionRepository;
        this.wppService = wppService;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody SendMessageRequest dto) {

        // 1. Validar cliente
        Optional<Client> clientOpt = clientService.findByApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid api key"));
        }
        Client client = clientOpt.get();

        // 2. Validar sessão existe
        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(dto.getSession());
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

        // 6. Rate limiting POR SESSÃO (antibloqueio - 450 por sessão)
        if (!sessionUsageService.canSendMessage(dto.getSession())) {
            int sessionUsed = sessionUsageService.getUsageToday(dto.getSession());
            return ResponseEntity.status(429).body(Map.of(
                    "error", "session daily limit exceeded (anti-block protection)",
                    "limit", 450,
                    "used", sessionUsed,
                    "session", dto.getSession()));
        }

        // 7. Enviar mensagem via WPPConnect
        String token = session.getWppToken();
        if (token == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "wpp token missing for session"));
        }

        Map<?, ?> resp = wppService.sendMessage(
                dto.getSession(),
                token,
                dto.getTo(),
                dto.getMessage());

        // 8. Registrar uso para CLIENTE
        usageService.increment(apiKey, 1);

        // 9. Registrar uso para SESSÃO
        sessionUsageService.recordUsage(dto.getSession());

        // 10. Retornar resposta
        return ResponseEntity.ok(resp);
    }
}