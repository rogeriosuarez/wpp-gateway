package com.heureca.wppgateway.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.service.ApiClientService;

@RestController
@RequestMapping("/admin")
public class AuthController {

    private final ApiClientService apiClientService;

    public AuthController(ApiClientService clientService) {
        this.apiClientService = clientService;
    }

    /**
     * Creates an INTERNAL ApiClient (non-RapidAPI).
     *
     * This endpoint should be used only by administrators.
     * RapidAPI clients are automatically handled by the filter.
     */
    @PostMapping("/create-client")
    public ResponseEntity<?> createClient(
            @RequestParam String name,
            @RequestParam(required = false) Long dailyLimit) {

        ApiClient client = apiClientService.createInternalClient(name, dailyLimit);

        return ResponseEntity.ok(Map.of(
                "id", client.getId(),
                "name", client.getName(),
                "apiKey", client.getApiKey(),
                "dailyLimit", client.getDailyLimit(),
                "type", "INTERNAL"));
    }
}
