package com.example.wppgateway.repository;

import com.example.wppgateway.model.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByApiKey(String apiKey);
}
