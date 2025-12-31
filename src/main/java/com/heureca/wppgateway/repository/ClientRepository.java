package com.heureca.wppgateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.heureca.wppgateway.model.Client;

import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {
    Optional<Client> findByApiKey(String apiKey);
}
