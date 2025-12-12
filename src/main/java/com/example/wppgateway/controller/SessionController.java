package com.example.wppgateway.controller;

import com.example.wppgateway.model.Client;
import com.example.wppgateway.model.SessionEntity;
import com.example.wppgateway.repository.SessionRepository;
import com.example.wppgateway.service.ClientService;
import com.example.wppgateway.service.WppService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class SessionController {
    private final ClientService clientService;
    private final WppService wppService;
    private final SessionRepository sessionRepository;

    public SessionController(ClientService clientService, WppService wppService, SessionRepository sessionRepository) {
        this.clientService = clientService;
        this.wppService = wppService;
        this.sessionRepository = sessionRepository;
    }

    private Optional<Client> validateApiKey(String apiKey) {
        return clientService.findByApiKey(apiKey);
    }

    @PostMapping("/create-session")
    public ResponseEntity<?> createSession(@RequestHeader("X-Api-Key") String apiKey, @RequestBody Map<String,String> body) {
        var clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty()) return ResponseEntity.status(401).body(Map.of("error","invalid apiKey"));

        String sessionName = body.getOrDefault("sessionName", "s_"+apiKey.substring(0,8));
        SessionEntity s = new SessionEntity();
        s.setSessionName(sessionName);
        s.setClientApiKey(apiKey);
        s.setStatus("CREATED");
        sessionRepository.save(s);

        Map<?,?> tokenResp = wppService.generateWppToken(sessionName);
        String full = tokenResp.get("full") != null ? tokenResp.get("full").toString() : null;
        s.setWppToken(full != null ? full.replaceFirst("^wppconnect:", "") : null);
        s.setStatus("TOKEN_CREATED");
        sessionRepository.save(s);

        return ResponseEntity.ok(Map.of("session", sessionName, "wppTokenFull", tokenResp.get("full")));
    }

    @PostMapping("/start-session/{session}")
    public ResponseEntity<?> startSession(@RequestHeader("X-Api-Key") String apiKey, @PathVariable String session) {
        var clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty()) return ResponseEntity.status(401).body(Map.of("error","invalid apiKey"));

        var sOpt = sessionRepository.findBySessionName(session);
        if (sOpt.isEmpty()) return ResponseEntity.badRequest().body(Map.of("error","session not found"));

        SessionEntity s = sOpt.get();
        String token = s.getWppToken();
        if (token == null) return ResponseEntity.badRequest().body(Map.of("error","wpp token missing"));

        Map<?,?> resp = wppService.startSession(session, token);
        String status = resp.get("status") != null ? resp.get("status").toString() : "UNKNOWN";
        s.setStatus(status);
        sessionRepository.save(s);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/session/{session}")
    public ResponseEntity<?> getSession(@RequestHeader("X-Api-Key") String apiKey, @PathVariable String session) {
        var c = validateApiKey(apiKey);
        if (c.isEmpty()) return ResponseEntity.status(401).body(Map.of("error","invalid apiKey"));
        return sessionRepository.findBySessionName(session)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
