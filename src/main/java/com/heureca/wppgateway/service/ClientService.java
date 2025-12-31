package com.heureca.wppgateway.service;

import org.springframework.stereotype.Service;

import com.heureca.wppgateway.model.Client;
import com.heureca.wppgateway.repository.ClientRepository;
import com.heureca.wppgateway.util.ApiKeyGenerator;

import java.util.Optional;

@Service
public class ClientService {
    private final ClientRepository clientRepository;

    public ClientService(ClientRepository clientRepository) {
        this.clientRepository = clientRepository;
    }

    public Client createClient(String name, Integer dailyLimit) {
        Client c = new Client();
        c.setApiKey(ApiKeyGenerator.generate());
        c.setName(name);
        if (dailyLimit != null)
            c.setDailyLimit(dailyLimit);
        return clientRepository.save(c);
    }

    public Optional<Client> findByApiKey(String apiKey) {
        return clientRepository.findByApiKey(apiKey);
    }
}
