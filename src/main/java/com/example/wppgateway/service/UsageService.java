package com.example.wppgateway.service;

import com.example.wppgateway.model.MessageUsage;
import com.example.wppgateway.repository.MessageUsageRepository;
import org.springframework.stereotype.Service;
import java.time.LocalDate;

@Service
public class UsageService {
    private final MessageUsageRepository repo;
    public UsageService(MessageUsageRepository repo) { this.repo = repo; }

    public int getUsageToday(String apiKey) {
        return repo.findByClientApiKeyAndDate(apiKey, LocalDate.now()).map(MessageUsage::getCount).orElse(0);
    }

    public void increment(String apiKey, int delta) {
        MessageUsage mu = repo.findByClientApiKeyAndDate(apiKey, LocalDate.now()).orElseGet(() -> {
            MessageUsage m = new MessageUsage();
            m.setClientApiKey(apiKey);
            m.setDate(LocalDate.now());
            m.setCount(0);
            return m;
        });
        mu.setCount(mu.getCount() + delta);
        repo.save(mu);
    }
}
