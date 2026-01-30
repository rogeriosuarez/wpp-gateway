package com.heureca.wppgateway.service;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import com.heureca.wppgateway.model.ProviderSessionState;

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

    private ResponseEntity<?> forwardToWppConnect(
            String token,
            String url, HttpMethod method,
            Object body,
            String logName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);

        HttpEntity<?> request = new HttpEntity<>(body, headers);

        logger.debug("REQUEST WPPCONNECT ({}): {}", logName, url);

        try {
            ResponseEntity<String> response = rest.exchange(url, method, request, String.class);

            logger.debug("RESPONSE WPPCONNECT ({}): status={}", logName, response.getStatusCode());

            return ResponseEntity
                    .status(response.getStatusCode())
                    .body(response.getBody());

        } catch (HttpStatusCodeException e) {

            // 🔥 ERRO REAL DO PROVIDER
            logger.debug(
                    "ERROR WPPCONNECT ({}): status={} body={}",
                    logName,
                    e.getStatusCode(),
                    e.getResponseBodyAsString());

            return ResponseEntity
                    .status(e.getStatusCode())
                    .body(e.getResponseBodyAsString());

        } catch (Exception e) {

            logger.error("UNEXPECTED ERROR WPPCONNECT ({}): {}", logName, e.getMessage(), e);

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(Map.of(
                            "error", "WPP_CONNECT_UNAVAILABLE",
                            "message", e.getMessage()));
        }
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
                    "Failed to authenticate with WhatsApp provider. "
                            + "Please verify that the WPPConnect secret key is correctly configured.");

        } catch (HttpServerErrorException e) {
            logger.error("WPPCONNECT SERVER ERROR ({}): {}", e.getStatusCode(), e.getResponseBodyAsString());

            throw new RuntimeException(
                    "WhatsApp provider is currently unavailable. Please try again later.");
        }
    }

    public ResponseEntity<?> startSession(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/start-session", wppBaseUrl, sessionName);
        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-message");

    }

    public ResponseEntity<?> sendMessage(
            String session,
            String token,
            Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-message", wppBaseUrl, session);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-message");
    }
    public ResponseEntity<?> sendSeen(
            String session,
            String token,
            Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-seen", wppBaseUrl, session);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-seen");
    }

    // Adicionar este método
    public String getWppBaseUrl() {
        return this.wppBaseUrl;
    }

    /**
     * GET /api/{session}/all-messages-in-chat/{phone} Obtém todas as mensagens
     * de um chat específico
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
     * GET /api/{session}/all-unread-messages Obtém todas as mensagens não lidas
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
     * POST /api/{session}/send-image Envia imagem via base64
     */
    public ResponseEntity<?> sendImageBase64(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-image", wppBaseUrl, sessionName);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-image-base64");
    }

    /**
     * POST /api/{session}/send-file Envia documento via base64
     */
    public ResponseEntity<?> sendFile(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-file", wppBaseUrl, sessionName);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-file");
    }

    public ResponseEntity<?> sendFileBase64(String session, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-file-base64", wppBaseUrl, session);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-file-base64");
    }

    /**
     * POST /api/{session}/send-voice Envia áudio via base64
     */
    public ResponseEntity<?> sendVoice(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-voice", wppBaseUrl, sessionName);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-voice");
    }

    /**
     * POST /api/{session}/send-voice-base64 Envia áudio via base64
     */
    public ResponseEntity<?> sendVoiceBase64(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-voice-base64", wppBaseUrl, sessionName);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-voice-base64");
    }

    /**
     * POST /api/{session}/send-sticker Envia sticker (imagem convertida)
     */
    public ResponseEntity<?> sendSticker(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-sticker", wppBaseUrl, sessionName);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-sticker");
    }

    /**
     * POST /api/{session}/send-sticker-gif Envia sticker (imagem convertida)
     */
    public ResponseEntity<?> sendStickerGif(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-sticker-gif", wppBaseUrl, sessionName);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-sticker-gif");
    }

    // No seu WppService.java, adicione:
    /**
     * POST /api/{session}/send-list-message Envia lista interativa de opções
     */
    public ResponseEntity<?> sendListMessage(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-list-message", wppBaseUrl, sessionName);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-list-message");
    }

    /**
     * POST /api/{session}/send-poll-message Envia enquete interativa
     */
    public ResponseEntity<?> sendPollMessage(String sessionName, String token, Map<String, Object> body) {
        String url = String.format("%s/api/%s/send-poll-message", wppBaseUrl, sessionName);

        return forwardToWppConnect(
                token,
                url,
                HttpMethod.POST,
                body,
                "send-poll-message");
    }

    /**
     * GET /api/{session}/check-connection-session
     */
    public boolean isSessionConnected(String sessionName, String token) {
        String url = String.format("%s/api/%s/check-connection-session", wppBaseUrl, sessionName);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }

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
        if (token != null) {
            headers.setBearerAuth(token);
        }

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
        if (token != null) {
            headers.setBearerAuth(token);
        }

        HttpEntity<Void> req = new HttpEntity<>(headers);
        rest.exchange(url, HttpMethod.POST, req, Void.class);
    }

    /**
     * Best-effort cleanup used by DELETE session.
     * Returns true only if the provider confirms the session is closed or not
     * found.
     */
    public boolean safeLogoutAndClose(String sessionName, String token) {

        try {
            ProviderSessionState state = getProviderSessionState(sessionName, token);

            if (state == ProviderSessionState.CONNECTED) {
                logoutSession(sessionName, token);
            }

            // Verificação final
            ProviderSessionState after = getProviderSessionState(sessionName, token);

            return after == ProviderSessionState.NOT_FOUND
                    || after == ProviderSessionState.DISCONNECTED;

        } catch (Exception e) {
            logger.error("Cleanup failed for session {}", sessionName, e);
            return false;
        }
    }

    public ProviderSessionState getProviderSessionState(
            String sessionName,
            String token) {

        try {
            Map<?, ?> status = getSessionStatus(sessionName, token);
            String s = Objects.toString(status.get("status"), "").toLowerCase();

            return switch (s) {
                case "initializing" -> ProviderSessionState.INITIALIZING;
                case "qrcode" -> ProviderSessionState.QRCODE;
                case "connected" -> ProviderSessionState.CONNECTED;
                case "disconnected" -> ProviderSessionState.DISCONNECTED;
                default -> ProviderSessionState.UNKNOWN;
            };

        } catch (HttpClientErrorException.NotFound e) {
            return ProviderSessionState.NOT_FOUND;
        }
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
