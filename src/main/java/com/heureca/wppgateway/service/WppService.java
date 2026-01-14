package com.heureca.wppgateway.service;

import java.util.List;
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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
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
        logger.debug("REQUEST WPPCONNECT: {}", url);

        try {
            ResponseEntity<Map> r = rest.postForEntity(url, null, Map.class);
            return r.getBody();

        } catch (HttpClientErrorException e) {
            logger.error("WPPCONNECT AUTH ERROR ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());

            throw new RuntimeException(
                    "Failed to authenticate with WhatsApp provider. " +
                            "Please verify that the WPPConnect secret key is correctly configured.");

        } catch (HttpServerErrorException e) {
            logger.error("WPPCONNECT SERVER ERROR ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());

            throw new RuntimeException(
                    "WhatsApp provider is currently unavailable. Please try again later.");
        }
    }

    public Map<?, ?> startSession(String sessionName, String token) {
        String url = String.format("%s/api/%s/start-session", wppBaseUrl, sessionName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null)
            headers.setBearerAuth(token);
        Map<String, Object> body = Map.of("session", sessionName, "waitQrCode", false, "webhook", "");
        HttpEntity<Map<String, Object>> req = new HttpEntity<>(body, headers);
        logger.debug("REQUEST WPPCONNECT: {} :: req: {}", url, req);
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
        logger.debug("REQUEST WPPCONNECT: {} :: req: {}", url, req);
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
        logger.debug("REQUEST WPPCONNECT GET: {}", url);

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
        logger.debug("REQUEST WPPCONNECT GET: {}", url);

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
        logger.debug("REQUEST WPPCONNECT (send-image): {}", url);

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
        logger.debug("REQUEST WPPCONNECT (send-file): {}", url);

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
        logger.debug("REQUEST WPPCONNECT (send-voice): {}", url);

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
        logger.debug("REQUEST WPPCONNECT (send-sticker): {}", url);

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
        logger.debug("REQUEST WPPCONNECT (send-list-message): {}", url);
        logger.debug("Body: {}", body);

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
        logger.debug("REQUEST WPPCONNECT (send-buttons): {}", url);

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
        logger.debug("REQUEST WPPCONNECT (send-poll): {}", url);

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
        logger.debug("REQUEST WPPCONNECT (send-order-message): {}", url);

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
        logger.debug("REQUEST WPPCONNECT (send-reply): {}", url);

        ResponseEntity<Map> response = rest.exchange(url, HttpMethod.POST, request, Map.class);
        return response.getBody();
    }

    /**
     * GET /api/{session}/check-connection-session
     */
    public boolean isSessionConnected(String sessionName, String token) {
        String url = String.format("%s/api/%s/check-connection-session", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null)
            headers.setBearerAuth(token);

        HttpEntity<Void> req = new HttpEntity<>(headers);

        try {
            ResponseEntity<Map> resp = rest.exchange(url, HttpMethod.GET, req, Map.class);
            Object status = resp.getBody().get("status");
            return Boolean.TRUE.equals(status);
        } catch (Exception e) {
            logger.warn("WPPCONNECT check-connection failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * POST /api/{session}/logout-session
     */
    public void logoutSession(String sessionName, String token) {
        String url = String.format("%s/api/%s/logout-session", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null)
            headers.setBearerAuth(token);

        HttpEntity<Void> req = new HttpEntity<>(headers);
        rest.exchange(url, HttpMethod.POST, req, Void.class);
    }

    /**
     * POST /api/{session}/close-session
     */
    public void closeSession(String sessionName, String token) {
        String url = String.format("%s/api/%s/close-session", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null)
            headers.setBearerAuth(token);

        HttpEntity<Void> req = new HttpEntity<>(headers);
        rest.exchange(url, HttpMethod.POST, req, Void.class);
    }

    /**
     * Best-effort cleanup used by DELETE session
     */
    public void safeLogoutAndClose(String sessionName, String token) {
        boolean connected = isSessionConnected(sessionName, token);

        if (connected) {
            logger.debug("Session {} connected, logging out", sessionName);
            logoutSession(sessionName, token);
        }

        logger.debug("Closing session {}", sessionName);
        closeSession(sessionName, token);
    }

    public byte[] fetchQrCodeImage(String sessionName, String token) {

        String url = String.format("%s/api/%s/qrcode-session", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.IMAGE_PNG));
        if (token != null) {
            headers.setBearerAuth(token);
        }

        HttpEntity<Void> request = new HttpEntity<>(headers);

        logger.debug("REQUEST WPPCONNECT QR: {} headers={}", url, headers);

        ResponseEntity<byte[]> response = rest.exchange(url, HttpMethod.GET, request, byte[].class);

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalStateException(
                    "WPPConnect QRCode failed: " + response.getStatusCode());
        }

        logger.debug("WPPCONNECT QR OK size={} bytes", response.getBody().length);

        return response.getBody();
    }

    public Map<?, ?> getSessionStatus(String sessionName, String token) {

        String url = String.format("%s/api/%s/status-session", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(List.of(MediaType.ALL));

        if (token != null && !token.isBlank()) {
            headers.setBearerAuth(token);
        }

        HttpEntity<Void> req = new HttpEntity<>(headers);

        logger.debug("REQUEST WPPCONNECT STATUS: {} :: {}", url, headers);

        try {
            ResponseEntity<Map> resp = rest.exchange(
                    url,
                    HttpMethod.GET,
                    req,
                    Map.class);

            logger.debug("RESPONSE WPPCONNECT STATUS: {}", resp.getBody());
            return resp.getBody();

        } catch (HttpStatusCodeException e) {
            // 🔹 Pass-through controlado do erro do provider
            logger.warn("WPPCONNECT STATUS ERROR [{}]: {}", e.getStatusCode(), e.getResponseBodyAsString());

            return Map.of(
                    "status", "ERROR",
                    "provider_status", e.getStatusCode().value(),
                    "message", e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error("WPPCONNECT STATUS UNEXPECTED ERROR", e);
            throw e;
        }
    }

}
