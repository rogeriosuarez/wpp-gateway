package com.heureca.wppgateway.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.ClientSource;
import com.heureca.wppgateway.repository.ApiClientRepository;

@Service
public class ApiClientService {

    private final ApiClientRepository repository;

    public ApiClientService(ApiClientRepository repository) {
        this.repository = repository;
    }

    public ApiClient getOrCreateRapidClient(String rapidApiKey) {
        return repository.findByApiKey(rapidApiKey)
                .orElseGet(() -> createRapidClient(rapidApiKey));
    }

    private ApiClient createRapidClient(String rapidApiKey) {
        ApiClient client = new ApiClient();
        client.setApiKey(rapidApiKey);
        client.setName("rapid-" + rapidApiKey.substring(0, 8));
        client.setDailyLimit(null); // unlimited
        client.setSource(ClientSource.RAPID);
        client.setDailyUsage(0L);

        return repository.save(client);
    }

    public ApiClient validateInternalClient(String apiKey) {
        return repository.findByApiKey(apiKey)
                .orElseThrow(() -> new RuntimeException("Invalid API Key"));
    }

    public void validateRateLimit(ApiClient client) {
        resetIfNewDay(client);

        if (client.getDailyLimit() != null &&
                client.getDailyUsage() >= client.getDailyLimit()) {
            throw new RuntimeException("Daily limit exceeded");
        }

        client.setDailyUsage(client.getDailyUsage() + 1);
        repository.save(client);
    }

    private void resetIfNewDay(ApiClient client) {
        if (!LocalDate.now().equals(client.getLastReset())) {
            client.setDailyUsage(0L);
            client.setLastReset(LocalDate.now());
        }
    }
}
