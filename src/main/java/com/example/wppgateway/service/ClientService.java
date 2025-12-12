package com.example.wppgateway.service;

import com.example.wppgateway.model.Client;
import com.example.wppgateway.repository.ClientRepository;
import com.example.wppgateway.util.ApiKeyGenerator;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClientService {
    private final ClientRepository clientRepository;
    public ClientService(ClientRepository clientRepository) { this.clientRepository = clientRepository; }

    public Client createClient(String name, Integer dailyLimit) {
        Client c = new Client();
        c.setApiKey(ApiKeyGenerator.generate());
        c.setName(name);
        if (dailyLimit != null) c.setDailyLimit(dailyLimit);
        return clientRepository.save(c);
    }

    public Optional<Client> findByApiKey(String apiKey) {
        return clientRepository.findByApiKey(apiKey);
    }
}
