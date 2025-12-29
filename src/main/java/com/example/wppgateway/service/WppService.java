package com.example.wppgateway.service;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class WppService {
    private static final Logger logger = LoggerFactory.getLogger(WppService.class);

    private final RestTemplate rest;
    @Value("${wpp.base-url}")
    private String wppBaseUrl;
    @Value("${wpp.secret-key}")
    private String wppSecretKey;

    public WppService(RestTemplate rest) {
        this.rest = rest;
    }

    public Map<?, ?> generateWppToken(String sessionName) {
        String url = String.format("%s/api/%s/%s/generate-token", wppBaseUrl, sessionName, wppSecretKey);
        logger.info("REQUEST WPPCONNECT: {}", url);
        ResponseEntity<Map> r = rest.postForEntity(url, null, Map.class);
        return r.getBody();
    }

    public Map<?, ?> startSession(String sessionName, String token) {
        String url = String.format("%s/api/%s/start-session", wppBaseUrl, sessionName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null)
            headers.setBearerAuth(token);
        Map<String, Object> body = Map.of("session", sessionName, "waitQrCode", true, "webhook", "");
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT: {} :: req: {}", url, req);
        ResponseEntity<Map> r = rest.exchange(url, HttpMethod.POST, req, Map.class);
        return r.getBody();
    }

    public Map<?, ?> sendMessage(String sessionName, String token, String to, String message) {
        String url = String.format("%s/api/%s/send-message", wppBaseUrl, sessionName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        Map<String, Object> body = Map.of("phone", to, "message", message);
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT: {} :: req: {}", url, req);
        ResponseEntity<Map> r = rest.exchange(url, HttpMethod.POST, req, Map.class);
        return r.getBody();
    }

    // Adicionar este método
    public String getWppBaseUrl() {
        return this.wppBaseUrl;
    }

    /**
     * GET /api/{session}/all-messages-in-chat/{phone}
     * Obtém todas as mensagens de um chat específico
     */
    public Map<?, ?> getAllMessagesInChat(String sessionName, String token, String phone) {
        String url = String.format("%s/api/%s/all-messages-in-chat/%s",
                wppBaseUrl, sessionName, phone);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        logger.info("REQUEST WPPCONNECT GET: {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.GET, request, Map.class);
        return response.getBody();
    }

    /**
     * GET /api/{session}/all-unread-messages
     * Obtém todas as mensagens não lidas
     */
    public Map<?, ?> getAllUnreadMessages(String sessionName, String token) {
        String url = String.format("%s/api/%s/all-unread-messages", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Void> request = new HttpEntity<>(headers);
        logger.info("REQUEST WPPCONNECT GET: {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.GET, request, Map.class);
        return response.getBody();
    }
    // No seu WppService.java existente, adicione estes métodos:

    /**
     * POST /api/{session}/send-image
     * Envia imagem via base64
     */
    public Map<?, ?> sendImage(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-image", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT (send-image): {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    /**
     * POST /api/{session}/send-file
     * Envia documento via base64
     */
    public Map<?, ?> sendFile(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-file", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT (send-file): {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    /**
     * POST /api/{session}/send-voice
     * Envia áudio via base64
     */
    public Map<?, ?> sendVoice(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-voice-base64", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT (send-voice): {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    /**
     * POST /api/{session}/send-sticker
     * Envia sticker (imagem convertida)
     */
    public Map<?, ?> sendSticker(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-sticker", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT (send-sticker): {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    // No seu WppService.java, adicione:

    /**
     * POST /api/{session}/send-list-message
     * Envia lista interativa de opções
     */
    public Map<?, ?> sendListMessage(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-list-message", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT (send-list-message): {}", url);
        logger.info("Body: {}", body);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    /**
     * POST /api/{session}/send-buttons (DEPRECATED mas ainda funciona)
     * Envia botões interativos
     */
    public Map<?, ?> sendButtons(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-buttons", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT (send-buttons): {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    /**
     * POST /api/{session}/send-poll-message
     * Envia enquete interativa
     */
    public Map<?, ?> sendPollMessage(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-poll-message", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT (send-poll): {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    /**
     * POST /api/{session}/send-order-message
     * Envia mensagem de pedido (para e-commerce)
     */
    public Map<?, ?> sendOrderMessage(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-order-message", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT (send-order-message): {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    /**
     * POST /api/{session}/send-reply
     * Envia mensagem com botões de resposta rápida
     */
    public Map<?, ?> sendReply(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-reply", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
        logger.info("REQUEST WPPCONNECT (send-reply): {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }
}
