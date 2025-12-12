package com.example.wppgateway.controller;

import com.example.wppgateway.model.Client;
import com.example.wppgateway.service.ClientService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin")
public class AuthController {
    private final ClientService clientService;
    public AuthController(ClientService clientService) { this.clientService = clientService; }

    @PostMapping("/create-client")
    public ResponseEntity<?> createClient(@RequestParam String name, @RequestParam(required = false) Integer dailyLimit) {
        Client c = clientService.createClient(name, dailyLimit);
        return ResponseEntity.ok(Map.of("apiKey", c.getApiKey(), "name", c.getName(), "dailyLimit", c.getDailyLimit()));
    }
}
