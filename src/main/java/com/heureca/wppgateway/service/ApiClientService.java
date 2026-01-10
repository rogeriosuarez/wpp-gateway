package com.heureca.wppgateway.service;

import java.time.LocalDate;

import org.springframework.stereotype.Service;

import com.heureca.wppgateway.exception.RateLimitExceededException;
import com.heureca.wppgateway.exception.UnauthorizedException;
import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.ClientSource;
import com.heureca.wppgateway.repository.ApiClientRepository;

@Service
public class ApiClientService {

    private final ApiClientRepository repository;

    public ApiClientService(ApiClientRepository repository) {
        this.repository = repository;
    }

    public ApiClient getOrCreateRapidClient(String apiKey) {
        return repository.findByApiKey(apiKey)
                .orElseGet(() -> createRapidClient(apiKey));
    }

    private ApiClient createRapidClient(String apiKey) {
        ApiClient client = new ApiClient();
        client.setApiKey(apiKey);
        client.setName("rapid-" + apiKey.substring(0, 8));
        client.setSource(ClientSource.RAPID);
        client.setDailyLimit(null); // unlimited
        client.setDailyUsage(0L);
        return repository.save(client);
    }

    public ApiClient validateInternalClient(String apiKey) {
        return repository.findByApiKey(apiKey)
                .orElseThrow(() -> new UnauthorizedException("Invalid API Key"));
    }

    public void validateRateLimit(ApiClient client) {
        resetIfNewDay(client);

        if (client.getDailyLimit() != null &&
                client.getDailyUsage() >= client.getDailyLimit()) {
            throw new RateLimitExceededException("Daily limit exceeded");
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
