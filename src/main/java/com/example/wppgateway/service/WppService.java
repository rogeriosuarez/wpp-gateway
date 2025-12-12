package com.example.wppgateway.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class WppService {
    private final RestTemplate rest;
    @Value("${wpp.base-url}") private String wppBaseUrl;
    @Value("${wpp.secret-key}") private String wppSecretKey;

    public WppService(RestTemplate rest) { this.rest = rest; }

    public Map<?,?> generateWppToken(String sessionName) {
        String url = String.format("%s/api/%s/%s/generate-token", wppBaseUrl, sessionName, wppSecretKey);
        ResponseEntity<Map> r = rest.postForEntity(url, null, Map.class);
        return r.getBody();
    }

    public Map<?,?> startSession(String sessionName, String token) {
        String url = String.format("%s/api/%s/start-session", wppBaseUrl, sessionName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        Map<String,Object> body = Map.of("session", sessionName, "waitQrCode", true, "webhook", "");
        HttpEntity<Map<String,Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map> r = rest.exchange(url, HttpMethod.POST, req, Map.class);
        return r.getBody();
    }

    public Map<?,?> sendMessage(String sessionName, String token, String to, String message) {
        String url = String.format("%s/api/%s/send-message", wppBaseUrl, sessionName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(token);
        Map<String,Object> body = Map.of("phone", to, "message", message);
        HttpEntity<Map<String,Object>> req = new HttpEntity<>(body, headers);
        ResponseEntity<Map> r = rest.exchange(url, HttpMethod.POST, req, Map.class);
        return r.getBody();
    }
}
