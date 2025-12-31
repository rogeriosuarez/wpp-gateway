package com.heureca.wppgateway.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.heureca.wppgateway.model.MessageUsage;

import java.time.LocalDate;
import java.util.Optional;

public interface MessageUsageRepository extends JpaRepository<MessageUsage, Long> {
    Optional<MessageUsage> findByClientApiKeyAndDate(String clientApiKey, LocalDate date);
}
