package com.example.wppgateway.controller;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.example.wppgateway.dto.CreateSessionRequest;
import com.example.wppgateway.model.Client;
import com.example.wppgateway.model.SessionEntity;
import com.example.wppgateway.repository.SessionRepository;
import com.example.wppgateway.service.ClientService;
import com.example.wppgateway.service.UsageService;
import com.example.wppgateway.service.WppService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api")
public class SessionController {
    private static final Logger logger = LoggerFactory.getLogger(SessionController.class);
    private final ClientService clientService;
    private final WppService wppService;
    private final SessionRepository sessionRepository;
    private final UsageService usageService;
    private final RestTemplate restTemplate;

    public SessionController(ClientService clientService, WppService wppService,
            SessionRepository sessionRepository, UsageService usageService,
            RestTemplate restTemplate) {
        this.clientService = clientService;
        this.wppService = wppService;
        this.sessionRepository = sessionRepository;
        this.usageService = usageService;
        this.restTemplate = restTemplate;
    }

    private Optional<Client> validateApiKey(String apiKey) {
        return clientService.findByApiKey(apiKey);
    }

    // Método auxiliar para chamar endpoints do WPPConnect
    private Map<?, ?> callWppConnectEndpoint(String sessionName, String token, String endpoint) {
        String url = String.format("%s/api/%s/%s",
                wppService.getWppBaseUrl(), sessionName, endpoint);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }

        HttpEntity<Void> request = new HttpEntity<>(headers);
        ResponseEntity<Map> response = restTemplate.exchange(
                url, HttpMethod.POST, request, Map.class);

        return response.getBody();
    }

    @PostMapping("/create-session")
    public ResponseEntity<?> createSession(@RequestHeader("X-Api-Key") String apiKey,
            @Valid @RequestBody CreateSessionRequest dto) {

        // Validar API Key
        Optional<Client> clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));
        }

        // Validar phone
        if (!dto.isPhoneValid()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "invalid phone number format",
                    "phone", dto.getPhone()));
        }

        String cleanPhone = dto.getCleanPhone();
        String sessionName = "wpp_" + cleanPhone;

        // VERIFICAR SE SESSÃO JÁ EXISTE PARA ESTE PHONE
        Optional<SessionEntity> existingSessionOpt = sessionRepository.findByPhone(cleanPhone);

        if (existingSessionOpt.isPresent()) {
            SessionEntity existingSession = existingSessionOpt.get();

            // Verificar se pertence ao MESMO cliente
            if (!apiKey.equals(existingSession.getClientApiKey())) {
                return ResponseEntity.status(403).body(Map.of(
                        "error", "phone already registered by another client",
                        "phone", cleanPhone));
            }

            // SE PERTENCE AO MESMO CLIENTE → ATUALIZAR TOKEN
            return updateSessionToken(existingSession, sessionName);
        }

        // SE NÃO EXISTIR → CRIAR NOVA (seu código atual)
        return createNewSession(apiKey, cleanPhone, sessionName, dto.getDescription());
    }

    private ResponseEntity<?> updateSessionToken(SessionEntity existingSession, String sessionName) {
        try {
            // Gerar NOVO token
            Map<?, ?> tokenResp = wppService.generateWppToken(sessionName);
            String token = tokenResp.get("token") != null ? tokenResp.get("token").toString() : null;

            // Atualizar sessão existente
            existingSession.setWppToken(token);
            existingSession.setStatus("TOKEN_UPDATED");
            existingSession.setCreatedAt(LocalDateTime.now()); // Resetar timestamp
            sessionRepository.save(existingSession);

            return ResponseEntity.ok(Map.of(
                    "action", "token_updated",
                    "session", sessionName,
                    "phone", existingSession.getPhone(),
                    "description", existingSession.getDescription(),
                    "status", existingSession.getStatus(),
                    "wppTokenFull", tokenResp.get("full")));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to update token",
                    "message", e.getMessage()));
        }
    }

    private ResponseEntity<?> createNewSession(String apiKey, String cleanPhone,
            String sessionName, String description) {
        // Código de criação original...
        SessionEntity s = new SessionEntity();
        s.setSessionName(sessionName);
        s.setClientApiKey(apiKey);
        s.setPhone(cleanPhone);
        s.setDescription(description);
        s.setStatus("CREATED");
        sessionRepository.save(s);

        // Gerar token
        Map<?, ?> tokenResp = wppService.generateWppToken(sessionName);
        String token = tokenResp.get("token") != null ? tokenResp.get("token").toString() : null;
        s.setWppToken(token != null ? token : null);
        s.setStatus("TOKEN_CREATED");
        sessionRepository.save(s);

        return ResponseEntity.ok(Map.of(
                "action", "session_created",
                "session", sessionName,
                "phone", cleanPhone,
                "description", description,
                "status", s.getStatus(),
                "wppTokenFull", tokenResp.get("full")));
    }

    @PostMapping("/start-session/{session}")
    public ResponseEntity<?> startSession(@RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session) {
        Optional<Client> clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty())
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));

        Optional<SessionEntity> sOpt = sessionRepository.findBySessionName(session);
        if (sOpt.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));

        SessionEntity s = sOpt.get();
        // Verificar se sessão pertence ao cliente
        if (!apiKey.equals(s.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
        }

        String token = s.getWppToken();
        if (token == null)
            return ResponseEntity.badRequest().body(Map.of("error", "wpp token missing"));

        Map<?, ?> resp = wppService.startSession(session, token);
        logger.info("RESPONSE WPPCONNECT: {}", resp);
        String status = resp.get("status") != null ? resp.get("status").toString() : "UNKNOWN";
        s.setStatus(status);
        // DEVERIA verificar se há sucesso mesmo sem campo "status"
        if (resp.containsKey("qrcode") || resp.containsKey("urlcode")) {
            s.setStatus("QRCODE");
        } else if (resp.toString().contains("connected") || resp.toString().contains("CONNECTED")) {
            s.setStatus("CONNECTED");
        } else {
            s.setStatus(status.toUpperCase());
        }

        sessionRepository.save(s);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/session/{session}")
    public ResponseEntity<?> getSession(@RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session) {
        Optional<Client> c = validateApiKey(apiKey);
        if (c.isEmpty())
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));

        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(session);
        if (sessionOpt.isEmpty())
            return ResponseEntity.notFound().build();

        SessionEntity s = sessionOpt.get();
        // Verificar se sessão pertence ao cliente
        if (!apiKey.equals(s.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
        }

        return ResponseEntity.ok(s);
    }

    @PostMapping("/logout-session/{session}")
    public ResponseEntity<?> logoutSession(@RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session) {
        Optional<Client> clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty())
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));

        Optional<SessionEntity> sOpt = sessionRepository.findBySessionName(session);
        if (sOpt.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));

        SessionEntity s = sOpt.get();
        // Verificar se sessão pertence ao cliente
        if (!apiKey.equals(s.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
        }

        String token = s.getWppToken();
        if (token == null)
            return ResponseEntity.badRequest().body(Map.of("error", "wpp token missing"));

        // Registrar uso (logout conta como 1 request)
        int used = usageService.getUsageToday(apiKey);
        if (used + 1 > clientOpt.get().getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of("error", "daily limit exceeded"));
        }

        // Chamar logout no WPPConnect
        Map<?, ?> resp = callWppConnectEndpoint(session, token, "logout-session");

        // Atualizar status da sessão
        s.setStatus("LOGGED_OUT");
        sessionRepository.save(s);

        // Registrar uso
        usageService.increment(apiKey, 1);

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/close-session/{session}")
    public ResponseEntity<?> closeSession(@RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session) {
        Optional<Client> clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty())
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));

        Optional<SessionEntity> sOpt = sessionRepository.findBySessionName(session);
        if (sOpt.isEmpty())
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));

        SessionEntity s = sOpt.get();
        // Verificar se sessão pertence ao cliente
        if (!apiKey.equals(s.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
        }

        String token = s.getWppToken();
        if (token == null)
            return ResponseEntity.badRequest().body(Map.of("error", "wpp token missing"));

        // Registrar uso (close conta como 1 request)
        int used = usageService.getUsageToday(apiKey);
        if (used + 1 > clientOpt.get().getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of("error", "daily limit exceeded"));
        }

        // Chamar close no WPPConnect
        Map<?, ?> resp = callWppConnectEndpoint(session, token, "close-session");

        // Atualizar status da sessão
        s.setStatus("CLOSED");
        s.setWppToken(null);
        sessionRepository.save(s);

        // Registrar uso
        usageService.increment(apiKey, 1);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/qrcode/{session}")
    public ResponseEntity<?> getQrCode(@RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session) {

        // Validações (igual ao seu código)
        Optional<Client> clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));
        }

        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(session);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));
        }

        SessionEntity s = sessionOpt.get();
        if (!apiKey.equals(s.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
        }

        // String status = s.getStatus();
        // if (!"QRCODE".equalsIgnoreCase(status)) {
        // return ResponseEntity.status(409).body(Map.of(
        // "error", "session not in qrcode state",
        // "status", status,
        // "hint", "Call /start-session first"));
        // }

        // Rate limiting
        int used = usageService.getUsageToday(apiKey);
        if (used + 1 > clientOpt.get().getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of("error", "daily limit exceeded"));
        }

        try {
            // Tentar obter QR code do WPPConnect
            byte[] qrCodeImage = getQrCodeImageFromWpp(session, s.getWppToken());

            // Registrar uso
            usageService.increment(apiKey, 1);

            // Converter para base64
            String base64Image = Base64.getEncoder().encodeToString(qrCodeImage);

            return ResponseEntity.ok(Map.of(
                    "type", "image",
                    "format", "png",
                    "encoding", "base64",
                    "data", base64Image,
                    "message", "Scan with WhatsApp",
                    "size_bytes", qrCodeImage.length));

        } catch (Exception e) {
            logger.error("Failed to get QR code", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "failed to get QR code",
                    "message", e.getMessage()));
        }
    }

    private byte[] getQrCodeImageFromWpp(String session, String token) {
        String url = String.format("%s/api/%s/qrcode-session",
                wppService.getWppBaseUrl(), session);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.IMAGE_PNG, MediaType.ALL));

        HttpEntity<Void> request = new HttpEntity<>(headers);

        // Usar RestTemplate com ByteArray para capturar imagem binária
        ResponseEntity<byte[]> response = restTemplate.exchange(
                url, HttpMethod.GET, request, byte[].class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return response.getBody();
        } else {
            throw new RuntimeException("WPPConnect returned error: " + response.getStatusCode());
        }
    }

    @GetMapping("/{session}/status-session")
    public ResponseEntity<?> getSessionStatus(@RequestHeader("X-Api-Key") String apiKey,
            @PathVariable String session) {

        // Validações básicas
        Optional<Client> clientOpt = validateApiKey(apiKey);
        if (clientOpt.isEmpty()) {
            return ResponseEntity.status(401).body(Map.of("error", "invalid apiKey"));
        }

        Optional<SessionEntity> sessionOpt = sessionRepository.findBySessionName(session);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "session not found"));
        }

        SessionEntity s = sessionOpt.get();

        // Verificar se sessão pertence ao cliente
        if (!apiKey.equals(s.getClientApiKey())) {
            return ResponseEntity.status(403).body(Map.of("error", "session does not belong to client"));
        }

        // Rate limiting
        int used = usageService.getUsageToday(apiKey);
        if (used + 1 > clientOpt.get().getDailyLimit()) {
            return ResponseEntity.status(429).body(Map.of("error", "daily limit exceeded"));
        }

        // Chamar status no WPPConnect (se quiser)
        // Map<?, ?> resp = callWppConnectEndpoint(session, s.getWppToken(),
        // "status-session");

        // Ou simplesmente retornar status do banco
        return ResponseEntity.ok(Map.of(
                "session", s.getSessionName(),
                "status", s.getStatus(),
                "phone", s.getPhone(),
                "createdAt", s.getCreatedAt(),
                "connected", "CONNECTED".equalsIgnoreCase(s.getStatus())));
    }
}