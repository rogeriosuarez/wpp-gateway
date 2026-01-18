package com.heureca.wppgateway.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.heureca.wppgateway.model.ApiClient;
import com.heureca.wppgateway.model.ClientSource;

public interface ApiClientRepository extends JpaRepository<ApiClient, Long> {
    Optional<ApiClient> findByApiKey(String apiKey);

    Optional<ApiClient> findByApiKeyAndSource(String apiKey, ClientSource rapid);
}
