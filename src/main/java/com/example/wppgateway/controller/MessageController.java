package com.example.wppgateway.controller;

import com.example.wppgateway.model.Client;
import com.example.wppgateway.model.SessionEntity;
import com.example.wppgateway.service.ClientService;
import com.example.wppgateway.service.UsageService;
import com.example.wppgateway.service.WppService;
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
    private final SessionRepository sessionRepository;
    private final WppService wppService;

    public MessageController(ClientService clientService, UsageService usageService,
                             SessionRepository sessionRepository, WppService wppService) {
        this.clientService = clientService;
        this.usageService = usageService;
        this.sessionRepository = sessionRepository;
        this.wppService = wppService;
    }

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(@RequestHeader("X-Api-Key") String apiKey,
                                         @Valid @RequestBody SendMessageRequest dto) {
        Optional<Client> cOpt = clientService.findByApiKey(apiKey);
        if (cOpt.isEmpty()) return ResponseEntity.status(401).body(Map.of("error","invalid api key"));

        Client client = cOpt.get();
        int used = usageService.getUsageToday(apiKey);
        if (used + 1 > client.getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of("error","daily limit exceeded"));
        }

        Optional<SessionEntity> sOpt = sessionRepository.findBySessionName(dto.getSession());
        if (sOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error","session not found"));

        SessionEntity s = sOpt.get();
        if (!"OPEN".equalsIgnoreCase(s.getStatus()) && !"QRCODE".equalsIgnoreCase(s.getStatus()) && !"CONNECTED".equalsIgnoreCase(s.getStatus())) {
            return ResponseEntity.status(409).body(Map.of("error","session not ready", "status", s.getStatus()));
        }

        Map<?,?> resp = wppService.sendMessage(dto.getSession(), s.getWppToken(), dto.getTo(), dto.getMessage());
        usageService.increment(apiKey, 1);
        return ResponseEntity.ok(resp);
    }
}
